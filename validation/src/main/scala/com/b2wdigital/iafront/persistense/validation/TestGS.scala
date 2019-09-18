package com.b2wdigital.iafront.persistense.validation

import org.apache.spark.sql.SparkSession._
import com.b2wdigital.iafront.persistense.gs._
import com.b2wdigital.iafront.session.SessionUtils
import org.apache.spark.sql.SaveMode

object TestGS extends App {
    
    val spark = SessionUtils.createSession(Some("local[1]"))

    spark.setupGS

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