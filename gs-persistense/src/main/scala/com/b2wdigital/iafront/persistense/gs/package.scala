package com.b2wdigital.iafront.persistense

import com.b2wdigital.iafront.persistense.configurations.ConfigurationUtils._
import org.apache.spark.sql.SparkSession

package object gs {
  
    implicit class GSConfig(sparkSession:SparkSession) {
        private val hadoopConfig = sparkSession.sparkContext.hadoopConfiguration
    
        def setupGS:Unit = {
          hadoopConfig.set("google.cloud.auth.service.account.enable", "true")
          setHadoopConf("fs.gs.project.id", "GCP_PROJECT_ID")(sparkSession)
    
          setHadoopConf("google.cloud.auth.service.account.json.keyfile", "GOOGLE_APPLICATION_CREDENTIALS_FILE")(sparkSession)
    
          hadoopConfig.set("fs.gs.inputstream.support.gzip.encoding.enable", "true")
          hadoopConfig.set("fs.gs.impl", "com.google.cloud.hadoop.fs.gcs.GoogleHadoopFileSystem")
          hadoopConfig.set("fs.AbstractFileSystem.gs.impl", "com.google.cloud.hadoop.fs.gcs.GoogleHadoopFS")
        }

        def setupGS(projectId:String, credentialsFile:String):Unit = {
          hadoopConfig.set("google.cloud.auth.service.account.enable", "true")
          hadoopConfig.set("fs.gs.project.id", projectId)
    
          hadoopConfig.set("google.cloud.auth.service.account.json.keyfile", credentialsFile)
    
          hadoopConfig.set("fs.gs.inputstream.support.gzip.encoding.enable", "true")
          hadoopConfig.set("fs.gs.impl", "com.google.cloud.hadoop.fs.gcs.GoogleHadoopFileSystem")
          hadoopConfig.set("fs.AbstractFileSystem.gs.impl", "com.google.cloud.hadoop.fs.gcs.GoogleHadoopFS")
        }
      }
}
