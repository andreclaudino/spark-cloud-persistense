name := "spark-cloud-persistense"
organization in ThisBuild := "com.b2w.iafront.persistense"
scalaVersion in ThisBuild := "2.11.12"
version := "1.0.0"

val sparkVersion = "2.4.3"

lazy val root =
  (project in file("."))
      .aggregate(base)
      .aggregate(s3)
      .aggregate(gs)

lazy val commonConfiguration = Seq(
  {
    assemblyMergeStrategy in assembly := {
      case PathList("javax", "servlet", xs @ _*) => MergeStrategy.last
      case PathList("javax", "activation", xs @ _*) => MergeStrategy.last
      case PathList("org", "apache", xs @ _*) => MergeStrategy.last
      case PathList("com", "google", xs @ _*) => MergeStrategy.last
      case PathList("com", "esotericsoftware", xs @ _*) => MergeStrategy.last
      case PathList("com", "codahale", xs @ _*) => MergeStrategy.last
      case PathList("com", "yammer", xs @ _*) => MergeStrategy.last
      case PathList("com", "amazonaws", xs @ _*) => MergeStrategy.last
      case PathList("com", "syncron", xs @ _*) => MergeStrategy.first
      case PathList("javax", "inject", xs @ _*) => MergeStrategy.first
      case PathList("org", "aopalliance", xs @ _*) => MergeStrategy.first
      case PathList("org", "fusesource", xs @ _*) => MergeStrategy.first
      case "about.html" => MergeStrategy.rename
      case "META-INF/ECLIPSEF.RSA" => MergeStrategy.last
      case "META-INF/mailcap" => MergeStrategy.last
      case "META-INF/mimetypes.default" => MergeStrategy.last
      case "plugin.properties" => MergeStrategy.last
      case "log4j.properties" => MergeStrategy.last
      case "mozilla/public-suffix-list.txt" => MergeStrategy.last
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    }
  },
  {
    /// Configurações para execução
    run in Compile := Defaults.runTask(fullClasspath in Compile, mainClass in (Compile, run), runner in (Compile, run)).evaluated
    runMain in Compile := Defaults.runMainTask(fullClasspath in Compile, runner in(Compile, run)).evaluated
  }
)

/////////////////////// base ////////////////////////////////////////
lazy val base  =
  Project("spark-cloud-persistense-base", file("base-persistense"))
    .settings(
      libraryDependencies ++= commonDependencies
    ).settings(commonConfiguration)

lazy val commonDependencies =
  Seq(
    "org.apache.spark" %% "spark-sql" % sparkVersion % "provided",
    "org.apache.spark" %% "spark-core" % sparkVersion % "provided"
  )

/////////////////////// s3 ///////////////////////////////////////////
lazy val s3  =
  Project("spark-cloud-persistense-s3", file("s3-persistense"))
    .dependsOn(base)
    .settings(
      libraryDependencies ++= commonDependencies ++ s3Dependencies
    ).settings(commonConfiguration)
    .settings(s3ShadeRules)

lazy val s3Dependencies = Seq(
  "com.amazonaws" % "aws-java-sdk" % "1.7.4",
  "org.apache.hadoop" % "hadoop-aws" % "2.7.3"
)

lazy val s3ShadeRules = {
  assemblyShadeRules in assembly ++= Seq(
  ShadeRule
    .rename("*" -> "com.b2wdigital.iafront.persistense.s3.shaded.@1")
    .inAll,
  ShadeRule
    .keep("org.apache.spark.**", "org.apache.hadoop.fs.s3a.S3AFileSystem")
    .inAll
  )
}
////////////////////// gs //////////////////////////////////////////
lazy val gs  =
  Project("spark-cloud-persistense-gs", file("gs-persistense"))
    .dependsOn(base)
    .settings(
      libraryDependencies ++= commonDependencies ++ gsDependencies
    ).settings(commonConfiguration)
    .settings(gsShadeRules)

lazy val gsDependencies = Seq(
  "commons-beanutils" % "commons-beanutils" % "1.9.4",
  "com.google.cloud.bigdataoss" % "gcs-connector" % "hadoop2-2.0.0"
      exclude("javax.jms", "jms")
      exclude("com.sun.jdmk", "jmxtools")
      exclude("com.sun.jmx", "jmxri")
      exclude("org.apache.hadoop", "hadopp-common")
      exclude("com.fasterxml.jackson.core", " jackson-databind")
      exclude("com.fasterxml.jackson.core", " jackson-core")
      exclude("com.fasterxml.jackson.core", " jackson-annotations")
)

lazy val gsShadeRules = {
  assemblyShadeRules in assembly ++= Seq(
  ShadeRule
    .rename("*" -> "com.b2wdigital.iafront.persistense.gs.shaded.@1")
    .inAll,
  ShadeRule
    .keep("org.apache.spark.**", "com.google.cloud.hadoop.fs.gcs.GoogleHadoopFS")
    .inAll
  )
}
/////////////////// validation //////////////////////////////////////
lazy val validation  =
  Project("validation", file("validation"))
    .dependsOn(gs)
    .dependsOn(s3)
    .settings(
      libraryDependencies ++= commonDependencies
    ).settings(commonConfiguration)


/////////////// Confiurations //////////////////////
logLevel in assembly := Level.Debug