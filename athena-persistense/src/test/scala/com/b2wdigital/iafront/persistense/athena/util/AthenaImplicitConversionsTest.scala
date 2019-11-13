package com.b2wdigital.iafront.persistense.athena.util

import java.sql.Date
import java.text.SimpleDateFormat

import com.b2wdigital.iafront.persistense.athena.utils.AthenaImplicitConversions._
import org.scalatest.{FlatSpec, Matchers}

class AthenaImplicitConversionsTest extends FlatSpec with Matchers {
  private val dateFormatter = new SimpleDateFormat("yyyy-MM-dd")

  "asInt" should "convert samples to Int" in {
    "20".asInt shouldBe 20
    "10000".asInt shouldBe 10000
    "1000.0".asInt shouldBe 1000
    "1000.3".asInt shouldBe 1000

    an[Exception] should be thrownBy "askjgad".asInt
    an[Exception] should be thrownBy "2000L".asInt
  }

  "asLong" should "convert samples to Long" in {
    "20".asInt shouldBe 20L
    "10000".asInt shouldBe 10000L
    "1000.0".asInt shouldBe 1000L
    "1000.3".asInt shouldBe 1000L

    an[Exception] should be thrownBy "askjgad".asInt
    an[Exception] should be thrownBy "2000L".asInt
  }

  "asDate" should "parse dates" in {
    "2018-20-10".asDate shouldBe new Date(dateFormatter.parse("2018-20-10").getTime)
    "2019-01-07".asDate shouldBe new Date(dateFormatter.parse("2019-01-07").getTime)

    an[Exception] should be thrownBy "2019-01".asDate
    an[Exception] should be thrownBy "q4213235234q5".asDate
  }
}

