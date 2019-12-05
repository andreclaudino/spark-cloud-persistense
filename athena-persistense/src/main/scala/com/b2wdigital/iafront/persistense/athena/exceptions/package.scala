package com.b2wdigital.iafront.persistense.athena

package object exceptions {
  class QueryErrorException(message:String, queryId:String) extends Exception(message)

  class QueryFailedException(message:String, queryId:String)
    extends QueryErrorException(message, queryId)
  class QueryCanceledException(message:String, queryId:String)
    extends QueryErrorException(message, queryId)
}
