package com.b2wdigital.iafront.session
import org.apache.spark.sql.SparkSession

object SessionUtils {
    
    def createSession(master:Option[String]) = master match
    {
        case Some(value) => SparkSession.builder.master(value).getOrCreate()
        case None => SparkSession.builder.getOrCreate()
    }
}