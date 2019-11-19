package com.b2wdigital.iafront.persistense

import software.amazon.awssdk.services.athena.model.QueryExecutionStatus

package object model {
  type AthenaRow = software.amazon.awssdk.services.athena.model.Row
  type SparkRow = org.apache.spark.sql.Row

  case class SchemaColumn(name:String, dataType:String)
  case class QueryResult(status:Option[QueryExecutionStatus],
                         schema:Seq[SchemaColumn],
                         outputLocation:String)

}
