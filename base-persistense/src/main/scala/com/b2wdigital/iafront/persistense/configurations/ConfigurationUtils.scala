package com.b2wdigital.iafront.persistense.configurations

import org.apache.spark.sql.SparkSession

private [persistense] object ConfigurationUtils {

  def setHadoopConf(key:String, envName:String, raises:Boolean=false)(implicit sparkSession:SparkSession):Option[String] = {
    sys.env.get(envName) match {
      case Some(value) =>
        sparkSession
          .sparkContext
          .hadoopConfiguration
          .set(key, value)

        Some(value)
      case None =>
        if(raises) throw new ConfigurationExceptions.ConfigurationEnvNotFoundException(envName)
        None
    }
  }

  def setSparkConf(key:String, envName:String, raises:Boolean=false)(implicit sparkSession:SparkSession):Option[String] = {
    sys.env.get(envName) match {
      case Some(value) =>
        sparkSession
          .conf
          .set(key, value)
        Some(value)
      case None =>
        if(raises) throw new ConfigurationExceptions.ConfigurationEnvNotFoundException(envName)
        None
    }
  }
}
