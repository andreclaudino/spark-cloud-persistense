package com.b2wdigital.iafront.persistense

package object model {

  case class State(status:String, changeReason:String)
  case class QueryResult(status:State, outputLocation:String)

}
