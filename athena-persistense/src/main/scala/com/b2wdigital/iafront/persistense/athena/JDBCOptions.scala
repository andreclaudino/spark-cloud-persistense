package com.b2wdigital.iafront.persistense.athena

import java.sql.{Connection, DriverManager}
import java.util.{Locale, Properties}

import com.simba.athena.amazonaws.regions.Regions
import org.apache.spark.sql.catalyst.util.CaseInsensitiveMap

/**
 * Options for the JDBC data source.
 */
class JDBCOptions(
                   @transient private val parameters: CaseInsensitiveMap[String])
  extends Serializable {

  import JDBCOptions._

  def this(parameters: Map[String, String]) = this(CaseInsensitiveMap(parameters))

  def this(url: String, table: String, parameters: Map[String, String]) = {
    this(CaseInsensitiveMap(parameters ++ Map(
      JDBCOptions.JDBC_URL -> url,
      JDBCOptions.JDBC_TABLE_NAME -> table)))
  }

  /**
   * Returns a property with all options.
   */
  val asProperties: Properties = {
    val properties = new Properties()
    parameters.originalMap.foreach { case (k, v) => properties.setProperty(k, v) }
    properties
  }

  /**
   * Returns a property with all options except Spark internal data source options like `url`,
   * `dbtable`, and `numPartition`. This should be used when invoking JDBC API like `Driver.connect`
   * because each DBMS vendor has its own property list for JDBC driver. See SPARK-17776.
   */
  val asConnectionProperties: Properties = {
    val properties = new Properties()
    parameters.originalMap.filterKeys(key => !jdbcOptionNames(key.toLowerCase(Locale.ROOT)))
      .foreach { case (k, v) => properties.setProperty(k, v) }

    properties
      .setProperty("aws_credentials_provider_class", "com.simba.athena.amazonaws.auth.EnvironmentVariableCredentialsProvider")

    properties
  }

  /// If there is a region defined, the machine is in AWS
  private def isAws = {Regions.getCurrentRegion != null}


  // ------------------------------------------------------------
  // Required parameters
  // ------------------------------------------------------------
  require(parameters.isDefinedAt(JDBC_TABLE_NAME), s"Option '$JDBC_TABLE_NAME' is required.")
  // a JDBC URL
  val url = if (parameters.get(ATHENA_REGION).isDefined) {
    s"jdbc:awsathena://athena.${parameters(ATHENA_REGION)}.amazonaws.com:443"
  } else {
    s"jdbc:awsathena://athena.${Regions.getCurrentRegion.getName}.amazonaws.com:443"
  }

  // name of table
  val tableOrQuery = parameters(JDBC_TABLE_NAME)

  // ------------------------------------------------------------
  // Optional parameters
  // ------------------------------------------------------------
  val driverClass = {
    val userSpecifiedDriverClass = if (parameters.get(JDBC_DRIVER_CLASS).isDefined) {
      parameters.get(JDBC_DRIVER_CLASS)
    } else {
      Option("com.simba.athena.jdbc.Driver")
    }
    //parameters.get(JDBC_DRIVER_CLASS)
    userSpecifiedDriverClass.foreach(DriverRegistry.register)

    // Performing this part of the logic on the driver guards against the corner-case where the
    // driver returned for a URL is different on the driver and executors due to classpath
    // differences.
    userSpecifiedDriverClass.getOrElse {
      DriverManager.getDriver(url).getClass.getCanonicalName
    }
  }

  // the number of partitions
  val numPartitions = parameters.get(JDBC_NUM_PARTITIONS).map(_.toInt)

  // ------------------------------------------------------------
  // Optional parameters only for reading
  // ------------------------------------------------------------
  // the column used to partition
  val partitionColumn = parameters.get(JDBC_PARTITION_COLUMN)
  // the lower bound of partition column
  val lowerBound = parameters.get(JDBC_LOWER_BOUND).map(_.toLong)
  // the upper bound of the partition column
  val upperBound = parameters.get(JDBC_UPPER_BOUND).map(_.toLong)
  require(partitionColumn.isEmpty ||
    (lowerBound.isDefined && upperBound.isDefined && numPartitions.isDefined),
    s"If '$JDBC_PARTITION_COLUMN' is specified then '$JDBC_LOWER_BOUND', '$JDBC_UPPER_BOUND'," +
      s" and '$JDBC_NUM_PARTITIONS' are required.")
  val fetchSize = {
    val size = parameters.getOrElse(JDBC_BATCH_FETCH_SIZE, "0").toInt
    require(size >= 0,
      s"Invalid value `${size.toString}` for parameter " +
        s"`$JDBC_BATCH_FETCH_SIZE`. The minimum value is 0. When the value is 0, " +
        "the JDBC driver ignores the value and does the estimates.")
    size
  }

  // ------------------------------------------------------------
  // Optional parameters only for writing
  // ------------------------------------------------------------
  // if to truncate the table from the JDBC database
  val isTruncate = parameters.getOrElse(JDBC_TRUNCATE, "false").toBoolean
  // the create table option , which can be table_options or partition_options.
  // E.g., "CREATE TABLE t (name string) ENGINE=InnoDB DEFAULT CHARSET=utf8"
  // TODO: to reuse the existing partition parameters for those partition specific options
  val createTableOptions = parameters.getOrElse(JDBC_CREATE_TABLE_OPTIONS, "")
  val createTableColumnTypes = parameters.get(JDBC_CREATE_TABLE_COLUMN_TYPES)
  val batchSize = {
    val size = parameters.getOrElse(JDBC_BATCH_INSERT_SIZE, "1000").toInt
    require(size >= 1,
      s"Invalid value `${size.toString}` for parameter " +
        s"`$JDBC_BATCH_INSERT_SIZE`. The minimum value is 1.")
    size
  }
  val isolationLevel =
    parameters.getOrElse(JDBC_TXN_ISOLATION_LEVEL, "READ_UNCOMMITTED") match {
      case "NONE" => Connection.TRANSACTION_NONE
      case "READ_UNCOMMITTED" => Connection.TRANSACTION_READ_UNCOMMITTED
      case "READ_COMMITTED" => Connection.TRANSACTION_READ_COMMITTED
      case "REPEATABLE_READ" => Connection.TRANSACTION_REPEATABLE_READ
      case "SERIALIZABLE" => Connection.TRANSACTION_SERIALIZABLE
    }
}

object JDBCOptions {
  private val jdbcOptionNames = collection.mutable.Set[String]()

  private def newOption(name: String): String = {
    jdbcOptionNames += name.toLowerCase(Locale.ROOT)
    name
  }

  val JDBC_URL:String = newOption("url")
  val JDBC_TABLE_NAME:String = newOption("dbtable")
  val JDBC_DRIVER_CLASS:String = newOption("driver")
  val JDBC_PARTITION_COLUMN:String = newOption("partitionColumn")
  val JDBC_LOWER_BOUND:String = newOption("lowerBound")
  val JDBC_UPPER_BOUND:String = newOption("upperBound")
  val JDBC_NUM_PARTITIONS:String = newOption("numPartitions")
  val JDBC_BATCH_FETCH_SIZE:String = newOption("fetchsize")
  val JDBC_TRUNCATE:String = newOption("truncate")
  val JDBC_CREATE_TABLE_OPTIONS:String = newOption("createTableOptions")
  val JDBC_CREATE_TABLE_COLUMN_TYPES:String = newOption("createTableColumnTypes")
  val JDBC_BATCH_INSERT_SIZE:String = newOption("batchsize")
  val JDBC_TXN_ISOLATION_LEVEL:String = newOption("isolationLevel")

  val ATHENA_REGION:String = newOption("region")

}