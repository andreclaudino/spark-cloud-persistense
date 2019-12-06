package com.b2wdigital.iafront.persistense

import org.apache.spark.sql.{DataFrame, DataFrameReader}

package object athena {

  implicit class AthenaDataFrameReader(reader: DataFrameReader) {

    /**
     * @param queryOrTableName A query or table name to be loaded
     * @param options A Map[Stirng, String] with options. Possible options are
     *                * `region`: the aws region name
     *                * `s3_staging_dir`: s3 output directory for query results
     * @return Dataframe with table or query results
     */
    def athena(queryOrTableName: String, options:Map[String,String]): DataFrame = {
      reader
        .format("com.b2wdigital.iafront.persistense.athena")
        .options(options)
        .option(JDBCOptions.JDBC_TABLE_NAME, s"($queryOrTableName)")
        .load
    }

    /**
     * @param queryOrTableName A query or table name to be loaded
     * @param s3StagingDir s3 output directory for query results
     * @param region Athena region to use. Defaults to us-east-1
     * @return Dataframe with table or query results
     */
    def athena(queryOrTableName:String, s3StagingDir:String, region:String="us-east-1"): DataFrame = {
      val options = Map("region" -> region, "s3_staging_dir" -> s3StagingDir)
      reader.athena(queryOrTableName, options)
    }

  }
}
