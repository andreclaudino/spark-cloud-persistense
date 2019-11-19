package com.b2wdigital.iafront.persistense.athena.aws

import com.b2wdigital.iafront.persistense.athena.utils
import com.b2wdigital.iafront.persistense.model._
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.athena.AthenaClient
import software.amazon.awssdk.services.athena.model._

import scala.collection.JavaConverters._

class QuerySubmitter(outputBucket:String, databaseNameOption:Option[String]=None,
                     sleepTImeMsOption:Option[Long]=None,
                     pageSizeOption:Option[Int]=None) {

  private val databaseName = databaseNameOption.getOrElse("default")
  private val sleepTImeMs = sleepTImeMsOption.getOrElse(200L)
  private val pageSize = pageSizeOption.getOrElse(1000)

  private val athenaClient:AthenaClient = {
    AthenaClient
      .builder
      .region(Region.US_EAST_1)
      .credentialsProvider(ProfileCredentialsProvider.create())
      .build
  }

  private val queryExecutionContext:QueryExecutionContext = {
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

  def getQueryExecutionResponse(queryExecutionId:String) = {
    val getQueryExecutionRequest =
      GetQueryExecutionRequest
        .builder()
        .queryExecutionId(queryExecutionId)
        .build()

    athenaClient.getQueryExecution(getQueryExecutionRequest)
  }

  private def reportStatus(queryExecutionId:String):Option[QueryExecutionStatus] = {
    utils.whiley(cond = true) {
      val queryExecutionResponse = getQueryExecutionResponse(queryExecutionId)
      val queryStatus = queryExecutionResponse.queryExecution().status()
            queryStatus.state match {
        case QueryExecutionState.QUEUED | QueryExecutionState.RUNNING =>
          Thread.sleep(sleepTImeMs)
          None
        case _ =>
          return Some(queryStatus)
      }
    }
  }

  private def translateSchema(resultSetMetadata: ResultSetMetadata):Seq[SchemaColumn] = {
    resultSetMetadata
      .columnInfo
      .asScala
      .map(createColumnSchema)
  }

  private def createColumnSchema(columnInfo: ColumnInfo):SchemaColumn = {
    val columnType =
      columnInfo.`type`() match {
        case "varchar" => "string"
        case "tinyint" | "smallint" | "integer" => "int"
        case "bigint" => "long"
        case `type`:String => `type`
      }

    SchemaColumn(columnInfo.name(), columnType)
  }

  private def getSchema(queryResultsRequest: GetQueryResultsRequest):Seq[SchemaColumn] = {
      translateSchema {
        athenaClient
          .getQueryResults(queryResultsRequest)
          .resultSet()
          .resultSetMetadata()
      }
  }

  private def getQueryResults(queryExecutionId: String):GetQueryResultsRequest = {
    val queryResultsRequest =
      GetQueryResultsRequest
        .builder
        .maxResults(pageSize)
        .queryExecutionId(queryExecutionId)
        .build()
    queryResultsRequest
  }

  def executeQuery(query:String):QueryResult = {
    val queryId = startQueryExecution(query)
    val status = reportStatus(queryId)

    val queryResultsRequest = getQueryResults(queryId)
    val schema = getSchema(queryResultsRequest)

    val outputFile =
      getQueryExecutionResponse(queryId)
        .queryExecution
        .resultConfiguration
        .outputLocation
        .replaceFirst("s3://", "s3a://")

    QueryResult(status, schema, outputFile)
  }

}
