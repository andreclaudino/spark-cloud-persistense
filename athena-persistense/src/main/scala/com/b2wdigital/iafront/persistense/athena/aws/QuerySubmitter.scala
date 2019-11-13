package com.b2wdigital.iafront.persistense.athena.aws

import com.b2wdigital.iafront.persistense.athena.utils
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.athena.AthenaClient
import software.amazon.awssdk.services.athena.model.{GetQueryResultsResponse, _}
import com.b2wdigital.iafront.persistense.model._

import org.apache.spark.sql.{Row => SparkRowObj}

import scala.collection.JavaConverters._

class QuerySubmitter(outputBucket:String, databaseName:String="default", sleepTImeMs:Long=200, pageSize:Int=1000) {

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

  private def reportStatus(queryExecutionId:String):Option[QueryExecutionStatus] = {
    val getQueryExecutionRequest =
      GetQueryExecutionRequest
        .builder()
        .queryExecutionId(queryExecutionId)
        .build();

    utils.whiley(cond = true) {
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

  private def getQueryData(queryResultsRequest: GetQueryResultsRequest):Stream[SparkRow] = {
    getPages(queryResultsRequest)
      .flatMap(expandPage) // flatten and map
      .toStream
      .tail
  }

  private def expandPage(page:GetQueryResultsResponse):Seq[SparkRow] = {
    page
      .resultSet
      .rows
      .asScala
      .toArray
      .map(row => expandRow(row))
  }

  private def expandRow(row:AthenaRow):SparkRow = {
    val rowData =
      row
        .data
        .asScala
        .map(_.varCharValue)

    SparkRowObj.fromSeq(rowData)
  }

  private def getSchema(queryResultsRequest: GetQueryResultsRequest):Seq[SchemaColumn] = {
      translateSchema {
        athenaClient
          .getQueryResults(queryResultsRequest)
          .resultSet()
          .resultSetMetadata()
      }
  }

  private def getPages(queryResultsRequest: GetQueryResultsRequest):Iterator[GetQueryResultsResponse] = {
    athenaClient
      .getQueryResultsPaginator(queryResultsRequest)
      .stream // java stream of result pages
      .iterator
      .asScala  // scala seq of result pages
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

    val columnNames = schema.toSeq.map(_.name)
    val columnTypes = schema.toSeq.map(_.dataType)

    val data = getQueryData(queryResultsRequest)


    QueryResult(status, schema, data)
  }

}
