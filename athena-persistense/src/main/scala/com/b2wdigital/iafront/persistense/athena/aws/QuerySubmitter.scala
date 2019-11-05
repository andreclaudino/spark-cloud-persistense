package com.b2wdigital.iafront.persistense.athena.aws

import com.b2wdigital.iafront.persistense.athena.utils
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types.{BooleanType, DateType, DoubleType, LongType, StringType, StructField, StructType, TimestampType}
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.athena.{AthenaClient, AthenaClientBuilder}
import software.amazon.awssdk.services.athena.model._
import org.apache.spark.sql.{Row => SparkRow}
import software.amazon.awssdk.services.athena.model.{Row => AthenaRow}

import scala.collection.JavaConverters._

class QuerySubmitter(databaseNameOption:Option[String],
                     outputBucket:String,
                     sleepTImeMs:Long=200,
                     pageSize:Int=1000) {

  private val athenaClient:AthenaClient = {
    AthenaClient
      .builder
      .region(Region.US_EAST_1)
      .credentialsProvider(ProfileCredentialsProvider.create())
      .build
  };
  private val queryExecutionContext:QueryExecutionContext = {
    val databaseName = databaseNameOption.getOrElse("default")

    QueryExecutionContext
      .builder()
      .database(databaseName)
      .build()
  }
  private val resultConfiguration:ResultConfiguration = {
    ResultConfiguration
      .builder()
      .outputLocation(outputBucket)
      .build()
  }

  private def startQueryExecution(query:String):String = {
    val startQueryExecutionRequest =
      StartQueryExecutionRequest
        .builder
        .queryString(query)
        .queryExecutionContext(queryExecutionContext)
        .resultConfiguration(resultConfiguration)
        .build()

    athenaClient
      .startQueryExecution(startQueryExecutionRequest)
      .queryExecutionId()
  }

  private def reportStatus(queryExecutionId:String):Option[QueryExecutionStatus] = {
    val getQueryExecutionRequest =
      GetQueryExecutionRequest
        .builder()
        .queryExecutionId(queryExecutionId)
        .build();

    utils.whiley(true) {
      val getQueryExecutionResponse = athenaClient.getQueryExecution(getQueryExecutionRequest)
      val queryStatus = getQueryExecutionResponse.queryExecution().status()

      queryStatus.state match {
        case QueryExecutionState.QUEUED | QueryExecutionState.RUNNING =>
          Thread.sleep(sleepTImeMs)
          None
        case _ =>
          return Some(queryStatus)
      }
    }
  }

  private def getQueryData(queryExecutionId:String)(spark:SparkSession) = {
    val queryResultsRequest =
      GetQueryResultsRequest
        .builder
        .maxResults(pageSize)
        .queryExecutionId(queryExecutionId)
        .build();

    val metaResult =
      athenaClient
        .getQueryResultsPaginator(queryResultsRequest)

    val resultSetMetadata =
      athenaClient
      .getQueryResults(queryResultsRequest)
      .resultSet()
      .resultSetMetadata()

    val schema = translateSchema(resultSetMetadata)

    val results =
        metaResult
        .stream
        .iterator
        .asScala
        .toStream
        .map(print)

  }

  def athena2Spark(row:AthenaRow, schema:StructType):SparkRow = {
    ???
//    schema.fields.map({
//      column =>
//        row.data.get()
//    })
  }

  private def translateSchema(resultSetMetadata: ResultSetMetadata) = {
    val columnInfoList =
      resultSetMetadata
        .columnInfo
        .asScala

    val fields = columnInfoList.map(createColumnSchema).toArray
    new StructType(fields)
  }

  private def createColumnSchema(columnInfo: ColumnInfo):StructField = {
    /// Transform in recursive to deal structs
    val columnType =
      columnInfo.`type`() match {
        case "varchar" => StringType
        case "tinyint" | "smallint" | "integer" | "bigint" => LongType
        case "double" | "float" => DoubleType
        case "boolean" => BooleanType
        case "date" => DateType
        case "timestamp" => TimestampType
        case _ => StringType
      }

    StructField(columnInfo.name(), columnType)
  }

  def execute(query:String) = {
    val queryId = startQueryExecution(query)
    reportStatus(queryId)
  }
}
