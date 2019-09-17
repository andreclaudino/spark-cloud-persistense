name := "spark-cloud-persistense"
organization in ThisBuild := "com.b2w.iafront.persistense"
scalaVersion in ThisBuild := "2.11.12"

val sparkVersion = "2.4.3"

lazy val root =
  (project in file("."))

/////////////////////// base ////////////////////////////////////////
lazy val base  =
  Project("base-persistense", file("base"))
    .settings(
      libraryDependencies ++= baseDependencies
    )

lazy val baseDependencies =
  Seq(
    "org.apache.spark" %% "spark-sql" % sparkVersion % "provided",
    "org.apache.spark" %% "spark-core" % sparkVersion % "provided"
  )

/////////////////////// s3 ///////////////////////////////////////////
lazy val s3  =
  Project("s3-persistense", file("s3"))
    .dependsOn(base)
    .settings(
      libraryDependencies ++= s3Dependencies
    )

lazy val s3Dependencies = Seq(
  "com.amazonaws" % "aws-java-sdk" % "1.7.4",
  "org.apache.hadoop" % "hadoop-aws" % "2.7.3"
)

////////////////////// gs //////////////////////////////////////////
lazy val gs  =
  Project("gs-persistense", file("gs"))
    .dependsOn(base)
    .settings(
      libraryDependencies ++= gsDependencies
    )

lazy val gsDependencies = Seq(
  "commons-beanutils" % "commons-beanutils" % "1.9.4",
  "com.google.cloud.bigdataoss" % "gcs-connector" % "hadoop2-2.0.0" % "provided"
      exclude("javax.jms", "jms")
      exclude("com.sun.jdmk", "jmxtools")
      exclude("com.sun.jmx", "jmxri")
      exclude("org.apache.hadoop", "hadopp-common")
      exclude("com.fasterxml.jackson.core", " jackson-databind")
      exclude("com.fasterxml.jackson.core", " jackson-core")
      exclude("com.fasterxml.jackson.core", " jackson-annotations")
)