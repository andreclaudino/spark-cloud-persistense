package com.b2wdigital.iafront.persistense.athena.utils

import java.sql.Date
import java.text.SimpleDateFormat

object AthenaImplicitConversions {

  implicit class AthenaTypeConversion(value:String) {

    def asInt:Int = value.toDouble.toInt
    def asLong:Long = value.toDouble.toLong

    private val dateFormatter = new SimpleDateFormat("yyyy-MM-dd") //2019-07-29
    def asDate:Date = new Date(dateFormatter.parse(value).getTime)
    def asDate(format:String):Date = {
      val customFormatter = new SimpleDateFormat(format) //2019-07-29 08:18:26.074
      new Date(customFormatter.parse(value).getTime)
    }
  }
}
