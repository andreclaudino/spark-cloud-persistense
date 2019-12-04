package com.b2wdigital.iafront.persistense

import com.b2wdigital.iafront.persistense.configurations.ConfigurationUtils._
import org.apache.spark.sql.SparkSession

package object s3 {

  implicit class S3Config(sparkSession: SparkSession) {

    def setupS3:Unit = {
      setHadoopConf("fs.s3a.access.key", "AWS_ACCESS_KEY_ID")(sparkSession)
      setHadoopConf("fs.s3a.secret.key", "AWS_SECRET_ACCESS_KEY")(sparkSession)

      sparkSession.conf.set("spark.hadoop.fs.s3a.multiobjectdelete.enable","false")
      sparkSession.conf.set("spark.hadoop.fs.s3a.fast.upload","true")
      sparkSession.conf.set("spark.sql.parquet.filterPushdown", "true")
      sparkSession.conf.set("spark.sql.parquet.mergeSchema", "false")
      sparkSession.conf.set("spark.hadoop.mapreduce.fileoutputcommitter.algorithm.version", "2")
      sparkSession.conf.set("spark.speculation", "false")

      sparkSession.conf.set("spark.hadoop.fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem")
    }
  }

}