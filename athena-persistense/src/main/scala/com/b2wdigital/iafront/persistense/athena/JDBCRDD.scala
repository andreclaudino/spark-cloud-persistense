package com.b2wdigital.iafront.persistense.athena

import java.sql.{Connection, Date, ResultSet, SQLException, Statement, Timestamp}

import com.b2wdigital.iafront.persistense.athena.util.CompletionIterator
import org.apache.commons.lang3.StringUtils
import org.apache.spark.internal.Logging
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.jdbc.{JdbcDialect, JdbcDialects}
import org.apache.spark.sql.sources._
import org.apache.spark.sql.types._
import org.apache.spark.{InterruptibleIterator, Partition, SparkContext, TaskContext}

import scala.util.control.NonFatal
import collection.JavaConverters._

/**
 * Data corresponding to one partition of a JDBCRDD.
 */
case class JDBCPartition(whereClause: String, idx: Int) extends Partition {
  override def index: Int = idx
}

object JDBCRDD extends Logging {

  /**
   * Takes a (schema, table) specification and returns the table's Catalyst
   * schema.
   *
   * @param options - JDBC options that contains url, table and other information.
   * @return A StructType giving the table's Catalyst schema.
   * @throws SQLException if the table specification is garbage.
   * @throws SQLException if the table contains an unsupported type.
   */
  def resolveTable(options: JDBCOptions): StructType = {
    val url = options.url
    val table = options.tableOrQuery
    val dialect = JdbcDialects.get(url)
    val conn: Connection = JdbcUtils.createConnectionFactory(options)()
    try {
      //val statement = conn.prepareStatement(dialect.getSchemaQuery(table))
      val statement = conn.createStatement()
      try {
        val rs = statement.executeQuery(dialect.getSchemaQuery(table))
        try {
          JdbcUtils.getSchema(rs, dialect)
        } finally {
          rs.close()
        }
      } finally {
        statement.close()
      }
    } finally {
      conn.close()
    }
  }

  /**
   * Prune all but the specified columns from the specified Catalyst schema.
   *
   * @param schema  - The Catalyst schema of the master table
   * @param columns - The list of desired columns
   * @return A Catalyst schema corresponding to columns in the given order.
   */
  private def pruneSchema(schema: StructType, columns: Array[String]): StructType = {
    val fieldMap = Map(schema.fields.map(x => x.metadata.getString("name") -> x): _*)
    new StructType(columns.map(name => fieldMap(name)))
  }

  /**
   * Converts value to SQL expression.
   */
  private def compileValue(value: Any): Any = value match {
    case stringValue: String => s"'${escapeSql(stringValue)}'"
    case timestampValue: Timestamp => "'" + timestampValue + "'"
    case dateValue: Date => "'" + dateValue + "'"
    case arrayValue: Array[Any] => arrayValue.map(compileValue).mkString(", ")
    case _ => value
  }

  private def escapeSql(value: String): String =
    if (value == null) null else StringUtils.replace(value, "'", "''")

  /**
   * Turns a single Filter into a String representing a SQL expression.
   * Returns None for an unhandled filter.
   */
  def compileFilter(f: Filter, dialect: JdbcDialect): Option[String] = {
    def quote(colName: String): String = dialect.quoteIdentifier(colName)

    Option(f match {
      case EqualTo(attr, value) => s"${quote(attr)} = ${compileValue(value)}"
      case EqualNullSafe(attr, value) =>
        val col = quote(attr)
        s"(NOT ($col != ${compileValue(value)} OR $col IS NULL OR " +
          s"${compileValue(value)} IS NULL) OR ($col IS NULL AND ${compileValue(value)} IS NULL))"
      case LessThan(attr, value) => s"${quote(attr)} < ${compileValue(value)}"
      case GreaterThan(attr, value) => s"${quote(attr)} > ${compileValue(value)}"
      case LessThanOrEqual(attr, value) => s"${quote(attr)} <= ${compileValue(value)}"
      case GreaterThanOrEqual(attr, value) => s"${quote(attr)} >= ${compileValue(value)}"
      case IsNull(attr) => s"${quote(attr)} IS NULL"
      case IsNotNull(attr) => s"${quote(attr)} IS NOT NULL"
      case StringStartsWith(attr, value) => s"${quote(attr)} LIKE '$value%'"
      case StringEndsWith(attr, value) => s"${quote(attr)} LIKE '%$value'"
      case StringContains(attr, value) => s"${quote(attr)} LIKE '%$value%'"
      case In(attr, value) if value.isEmpty =>
        s"CASE WHEN ${quote(attr)} IS NULL THEN NULL ELSE FALSE END"
      case In(attr, value) => s"${quote(attr)} IN (${compileValue(value)})"
      case Not(f) => compileFilter(f, dialect).map(p => s"(NOT ($p))").orNull
      case Or(f1, f2) =>
        // We can't compile Or filter unless both sub-filters are compiled successfully.
        // It applies too for the following And filter.
        // If we can make sure compileFilter supports all filters, we can remove this check.
        val or = Seq(f1, f2).flatMap(compileFilter(_, dialect))
        if (or.size == 2) {
          or.map(p => s"($p)").mkString(" OR ")
        } else {
          null
        }
      case And(f1, f2) =>
        val and = Seq(f1, f2).flatMap(compileFilter(_, dialect))
        if (and.size == 2) {
          and.map(p => s"($p)").mkString(" AND ")
        } else {
          null
        }
      case _ => null
    })
  }

  /**
   * Build and return JDBCRDD from the given information.
   *
   * @param sc              - Your SparkContext.
   * @param schema          - The Catalyst schema of the underlying database table.
   * @param requiredColumns - The names of the columns to SELECT.
   * @param filters         - The filters to include in all WHERE clauses.
   * @param parts           - An array of JDBCPartitions specifying partition ids and
   *                        per-partition WHERE clauses.
   * @param options         - JDBC options that contains url, table and other information.
   * @return An RDD representing "SELECT requiredColumns FROM fqTable".
   */
  def scanTable(
                 sc: SparkContext,
                 schema: StructType,
                 requiredColumns: Array[String],
                 filters: Array[Filter],
                 parts: Array[Partition],
                 options: JDBCOptions): RDD[InternalRow] = {
    val url = options.url
    val dialect = JdbcDialects.get(url)
    val quotedColumns = requiredColumns.map(colName => dialect.quoteIdentifier(colName))
    new JDBCRDD(
      sc,
      JdbcUtils.createConnectionFactory(options),
      pruneSchema(schema, requiredColumns),
      quotedColumns,
      filters,
      parts,
      url,
      options)
  }
}

/**
 * An RDD representing a table in a database accessed via JDBC.  Both the
 * driver code and the workers must be able to access the database; the driver
 * needs to fetch the schema while the workers need to fetch the data.
 */
private[athena] class JDBCRDD(
                               sc: SparkContext,
                               getConnection: () => Connection,
                               schema: StructType,
                               columns: Array[String],
                               filters: Array[Filter],
                               partitions: Array[Partition],
                               url: String,
                               options: JDBCOptions)
  extends RDD[InternalRow](sc, Nil) {

  /**
   * Retrieve the list of partitions corresponding to this RDD.
   */
  override def getPartitions: Array[Partition] = partitions

  /**
   * `columns`, but as a String suitable for injection into a SQL query.
   */
  private val columnList: String = {
    val sb = new StringBuilder()
    columns.foreach(x => sb.append(",").append(x))
    if (sb.isEmpty) "1" else sb.substring(1)
  }

  /**
   * `filters`, but as a WHERE clause suitable for injection into a SQL query.
   */
  private val filterWhereClause: String =
    filters
      .flatMap(JDBCRDD.compileFilter(_, JdbcDialects.get(url)))
      .map(p => s"($p)").mkString(" AND ")

  /**
   * A WHERE clause representing both `filters`, if any, and the current partition.
   */
  private def getWhereClause(part: JDBCPartition): String = {
    if (part.whereClause != null && filterWhereClause.length > 0) {
      "WHERE " + s"($filterWhereClause)" + " AND " + s"(${part.whereClause})"
    } else if (part.whereClause != null) {
      "WHERE " + part.whereClause
    } else if (filterWhereClause.length > 0) {
      "WHERE " + filterWhereClause
    } else {
      ""
    }
  }

  /**
   * Runs the SQL query against the JDBC driver.
   *
   */
  override def compute(thePart: Partition, context: TaskContext): Iterator[InternalRow] = {
    var closed = false
    var rs: ResultSet = null
    var stmt: Statement = null
    var conn: Connection = null

    def close() {
      if (closed) return
      try {
        if (null != rs) {
          rs.close()
        }
      } catch {
        case e: Exception => logWarning("Exception closing resultset", e)
      }
      try {
        if (null != stmt) {
          stmt.close()
        }
      } catch {
        case e: Exception => logWarning("Exception closing statement", e)
      }
      try {
        if (null != conn) {
          if (!conn.isClosed && !conn.getAutoCommit) {
            try {
              conn.commit()
            } catch {
              case NonFatal(e) => logWarning("Exception committing transaction", e)
            }
          }
          conn.close()
        }
        logInfo("closed connection")
      } catch {
        case e: Exception => logWarning("Exception closing connection", e)
      }
      closed = true
    }

//    context.addTaskCompletionListener { context:TaskContext => close() }

    val inputMetrics = context.taskMetrics().inputMetrics
    val part = thePart.asInstanceOf[JDBCPartition]
    conn = getConnection()
    val dialect = JdbcDialects.get(url)
    dialect.beforeFetch(conn, options.asConnectionProperties.asScala.toMap)

    // H2's JDBC driver does not support the setSchema() method.  We pass a
    // fully-qualified table name in the SELECT statement.  I don't know how to
    // talk about a table in a completely portable way.

    val myWhereClause = getWhereClause(part)

    val sqlText = s"SELECT $columnList FROM ${options.tableOrQuery} $myWhereClause"
    //    stmt = conn.prepareStatement(sqlText,
    //      ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
    stmt = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
    stmt.setFetchSize(options.fetchSize)
    rs = stmt.executeQuery(sqlText)
    val rowsIterator = JdbcUtils.resultSetToSparkInternalRows(rs, schema, inputMetrics)

    CompletionIterator[InternalRow, Iterator[InternalRow]](
      new InterruptibleIterator(context, rowsIterator), close())
  }
}
