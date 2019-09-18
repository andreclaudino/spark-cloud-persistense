package com.b2wdigital.iafront.persistense

import com.b2wdigital.iafront.persistense.configurations.ConfigurationUtils._
import org.apache.spark.sql.SparkSession

package object s3 {

  implicit class S3Config(sparkSession: SparkSession) {

    def setupS3:Unit = {
      setHadoopConf("fs.s3a.access.key", "AWS_ACCESS_KEY_ID")(sparkSession)
      setHadoopConf("fs.s3a.secret.key", "AWS_SECRET_ACCESS_KEY")(sparkSession)

      setHadoopConf("fs.s3a.awsAccessKeyId", "AWS_ACCESS_KEY_ID")(sparkSession)
      setHadoopConf("fs.s3a.sawsSecretAccessKey", "AWS_SECRET_ACCESS_KEY")(sparkSession)


      sparkSession.conf.set("spark.hadoop.fs.s3a.aws.credentials.provider",
        "com.amazonaws.auth.EnvironmentVariableCredentialsProvider")
      sparkSession.conf.set("spark.hadoop.fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem")
    }
  }

}