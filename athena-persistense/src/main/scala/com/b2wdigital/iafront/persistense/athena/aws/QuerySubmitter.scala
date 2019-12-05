package com.b2wdigital.iafront.persistense.athena.aws

import com.b2wdigital.iafront.persistense.utils._
import com.b2wdigital.iafront.persistense.model._
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider
import com.amazonaws.regions.Regions
import com.amazonaws.services.athena.model.{GetQueryExecutionRequest, GetQueryExecutionResult, QueryExecutionContext, QueryExecutionState, QueryExecutionStatus, StartQueryExecutionRequest}
import com.amazonaws.services.athena.{AmazonAthena, AmazonAthenaClientBuilder}
import com.b2wdigital.iafront.persistense.athena.exceptions._

private [persistense] class QuerySubmitter(outputBucket:String, databaseNameOption:Option[String]=None,
                     sleepTImeMsOption:Option[Long]=None) {

  private val databaseName = databaseNameOption.getOrElse("default")
  private val sleepTImeMs = sleepTImeMsOption.getOrElse(200L)

  private val athenaClient:AmazonAthena = {
    AmazonAthenaClientBuilder
      .standard
      .withRegion(Regions.US_EAST_1)
      .withCredentials(new EnvironmentVariableCredentialsProvider)
      .build()
  }

  private val queryExecutionContext:QueryExecutionContext = {
    new QueryExecutionContext()
      .withDatabase(databaseName)
  }

  private def startQueryExecution(query:String):String = {
    val startQueryExecutionRequest =
      new StartQueryExecutionRequest()
        .withQueryString(query)
        .withQueryExecutionContext(queryExecutionContext)

    athenaClient
      .startQueryExecution(startQueryExecutionRequest)
      .getQueryExecutionId
  }

  def getQueryExecutionResponse(queryExecutionId:String):GetQueryExecutionResult = {
    val getQueryExecutionRequest =
      new GetQueryExecutionRequest()
        .withQueryExecutionId(queryExecutionId)

    athenaClient.getQueryExecution(getQueryExecutionRequest)
  }

  private def reportStatus(queryExecutionId:String):Option[QueryExecutionStatus] = {
    whiley(true) {
      val queryExecutionResponse = getQueryExecutionResponse(queryExecutionId)
      val queryStatus = queryExecutionResponse.getQueryExecution.getStatus

      QueryExecutionState.fromValue(queryStatus.getState) match {
        case QueryExecutionState.QUEUED | QueryExecutionState.RUNNING =>
          Thread.sleep(sleepTImeMs)
          None
        case QueryExecutionState.FAILED =>
          throw new QueryFailedException(queryStatus.getStateChangeReason, queryExecutionId)
        case QueryExecutionState.CANCELLED =>
          throw new QueryCanceledException(queryStatus.getStateChangeReason, queryExecutionId)
        case QueryExecutionState.SUCCEEDED =>
          return Some(queryStatus)
      }
    }
  }


  def executeQuery(query:String):QueryResult = {
    val queryId = startQueryExecution(query)
    val status = reportStatus(queryId).get

    val outputLocation =
      getQueryExecutionResponse(queryId)
        .getQueryExecution
        .getResultConfiguration
        .getOutputLocation
        .replaceFirst("s3://", "s3a://")

    val state = State(status.getState, status.getStateChangeReason)
    QueryResult(state, outputLocation)
  }

}
