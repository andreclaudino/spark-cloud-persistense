package com.b2wdigital.iafront.persistense

import com.b2wdigital.iafront.persistense.athena.aws.QuerySubmitter
import org.apache.spark.sql.{DataFrame, SparkSession}
import com.b2wdigital.iafront.persistense.s3._

package object athena {

  implicit class AthenaConfig(sparkSession: SparkSession) {

    def setupAthena():Unit = {
      sparkSession.setupS3
    }

    def readAthena(query:String, outputBubucket:String, databaseNameOption:Option[String]=None,
                   sleepTImeMsOption:Option[Long]=None):DataFrame = {
      val submitter = new QuerySubmitter(outputBubucket, databaseNameOption, sleepTImeMsOption)

      val result = submitter.executeQuery(s"select * from ($query)")

      sparkSession
        .read
        .option("header", "true")
        .csv(result.outputLocation)
    }
  }
}
