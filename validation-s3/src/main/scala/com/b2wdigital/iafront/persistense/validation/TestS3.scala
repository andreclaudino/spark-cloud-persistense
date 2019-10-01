package com.b2wdigital.iafront.persistense.validation

import com.b2wdigital.iafront.persistense.s3._
import com.b2wdigital.iafront.session.SessionUtils
import org.apache.spark.sql.SaveMode

object TestS3 extends App {

    val spark =
        if(args.length > 2){
            SessionUtils.createSession(Some(args(2)))
        } else
        {
            SessionUtils.createSession(None)
        }

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