package com.b2wdigital.iafront.persistense

import com.b2wdigital.iafront.persistense.configurations.ConfigurationUtils._
import org.apache.spark.sql.SparkSession

package object athena {

  implicit class AthenaConfig(sparkSession: SparkSession) {

    def setupS3:Unit = {
      setHadoopConf("db.athena.access.key", "AWS_ACCESS_KEY_ID")(sparkSession)
      setHadoopConf("db.athena.secret.key", "AWS_SECRET_ACCESS_KEY")(sparkSession)
    }
  }

}
