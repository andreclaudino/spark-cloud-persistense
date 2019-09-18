package com.b2wdigital.iafront.persistense.validation

import org.apache.spark.sql.SparkSession._
import com.b2wdigital.iafront.persistense.s3._
import com.b2wdigital.iafront.session.SessionUtils
import org.apache.spark.sql.SaveMode

object TestS3 extends App {
    
    val spark = SessionUtils.createSession(Some("local[1]"))

    spark.setupS3

    val df =
    spark
      .read
      .json(args(0))

    df.printSchema
    
    df.show

    df
    .write
    .format("json")
    .mode(SaveMode.Append)
    .save(args(1))

    spark.close
}