name := "spark-cloud-persistense"
organization in ThisBuild := "com.b2wdigital.iafront.persistense"
scalaVersion in ThisBuild := "2.11.12"

publishTo := sonatypePublishTo.value
publishConfiguration := publishConfiguration.value.withOverwrite(true)
publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true)
publishMavenStyle := true
licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))
sonatypeProfileName := "com.b2wdigital"

import xerial.sbt.Sonatype._
sonatypeProjectHosting := Some(GitHubHosting("andreclaudino", "spark-cloud-persistense", ""))
homepage := Some(url(s"https://github.com/andreclaudino/spark-cloud-persistense"))
scmInfo := Some(
  ScmInfo(url("https://github.com/andreclaudino/SparkSinfony"), "scm:git@github.com:andreclaudino/spark-cloud-persistense.git")
)

val commonVersion = "1.0.3"

version := commonVersion

val awsServicesVersion = "2.10.7"

val sparkVersion = "2.4.4"

lazy val root =
  (project in file("."))
      .aggregate(base)
      .aggregate(s3)
      .aggregate(gs)

/// Subprojects
lazy val commonConfiguration = Seq(
  {
    assemblyMergeStrategy in assembly := {
      case PathList("javax", "servlet", xs @ _*) => MergeStrategy.last
      case PathList("javax", "activation", xs @ _*) => MergeStrategy.last
      case PathList("org", "apache", xs @ _*) => MergeStrategy.last
      case PathList("org", "lo4j", xs @ _*) => MergeStrategy.last
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
  },
  {
    runMain in Compile := Defaults.runMainTask(fullClasspath in Compile, runner in(Compile, run)).evaluated
  },
  {
    version := commonVersion
  }
)

/////////////////////// base ////////////////////////////////////////
lazy val base  =
  Project("spark-cloud-persistense-base", file("base-persistense"))
    .settings(
      Seq(libraryDependencies ++= commonDependencies) ++ commonConfiguration ++ publishingConfiguration("spark-cloud-persistense-base"))
    ).settings(commonConfiguration ++ publishingConfiguration("spark-cloud-persistense-base"))

lazy val commonDependencies =
  Seq(
    "org.apache.spark" %% "spark-sql" % sparkVersion % "provided",
    "org.apache.spark" %% "spark-core" % sparkVersion % "provided"
  )

/////////////////////// s3 ///////////////////////////////////////////
lazy val s3  =
  Project("spark-cloud-persistense-s3", file("s3-persistense"))
    .dependsOn(base)
    .settings(libraryDependencies ++= commonDependencies ++ s3Dependencies)
      libraryDependencies ++= commonDependencies ++ s3Dependencies
    )
    .settings(artifact in (Compile, assembly) := {
      val art = (artifact in (Compile, assembly)).value
      art.withClassifier(Some("assembly"))
    })
  .settings(addArtifact(artifact in (Compile, assembly), assembly))
  .settings(commonConfiguration ++ publishingConfiguration("spark-cloud-persistense-s3"))
//  .settings(s3ShadeRules)

lazy val s3Dependencies = Seq(
  "com.amazonaws" % "aws-java-sdk" % "1.7.4"
    exclude("com.fasterxml.jackson.core", "jackson-databind"),
  "org.apache.hadoop" % "hadoop-aws" % "2.7.3"
    exclude("com.fasterxml.jackson.core", "jackson-databind"),
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.6.7"
)

lazy val s3ShadeRules = {
  assemblyShadeRules in assembly ++= Seq(
  ShadeRule
    .rename("*" -> "com.b2wdigital.iafront.persistense.s3.shaded.@1")
    .inAll,
  ShadeRule
    .keep(
      "org.apache.**",
      "org.apache.hadoop.fs.s3a.S3AFileSystem",
      "org.log4j.**",
      "com.b2wdigital.iafront.persistense.s3.**")
    .inAll
  )
}
////////////////////// gs //////////////////////////////////////////
lazy val gs  =
  Project("spark-cloud-persistense-gs", file("gs-persistense"))
    .dependsOn(base)
    .settings(
      libraryDependencies ++= commonDependencies ++ gsDependencies
    )
    .settings(commonConfiguration ++ publishingConfiguration("spark-cloud-persistense-gs"))

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
    .keep(
      "org.apache.**",
      "com.google.cloud.hadoop.fs.gcs.GoogleHadoopFS",
      "org.log4j.**",
      "com.b2wdigital.iafront.persistense.gs.**")
    .inAll
  )
}
/////////////////// validation //////////////////////////////////////
lazy val validationS3  =
  Project("validation-s3", file("validation-s3"))
    .dependsOn(s3)
    .settings(
      libraryDependencies ++= commonDependencies
    ).settings(commonConfiguration)
    .settings(artifact in (Compile, assembly) := {
      val art = (artifact in (Compile, assembly)).value
      art.withClassifier(Some("assembly"))
    })
    .settings(addArtifact(artifact in (Compile, assembly), assembly))
    .settings(commonConfiguration ++ Seq(publishArtifact := false))

lazy val validationGS  =
  Project("validation-gs", file("validation-gs"))
    .dependsOn(gs)
    .settings(
      libraryDependencies ++= commonDependencies
    ).settings(commonConfiguration)
    .settings(artifact in (Compile, assembly) := {
      val art = (artifact in (Compile, assembly)).value
      art.withClassifier(Some("assembly"))
    })
    .settings(addArtifact(artifact in (Compile, assembly), assembly))
    .settings(commonConfiguration ++ Seq(publishArtifact := false))


/////////////// Configurations //////////////////////
logLevel in assembly := Level.Debug


/////////////// Publishing ///////////////

def publishingConfiguration(name:String):sbt.Def.SettingsDefinition = Seq(
  publishTo := sonatypePublishToBundle.value,
  publishConfiguration := publishConfiguration.value.withOverwrite(true),
  publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true),
  publishMavenStyle := true,
  licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
  sonatypeProfileName := "com.b2wdigital",
  {
    import xerial.sbt.Sonatype._
    sonatypeProjectHosting := Some(GitHubHosting("andreclaudino", name, ""))
  },
  homepage := Some(url(s"https://github.com/andreclaudino/spark-cloud-persistense")),
  scmInfo := Some(
    ScmInfo(url("https://github.com/andreclaudino/SparkSinfony"), "scm:git@github.com:andreclaudino/spark-cloud-persistense.git")
  )
)
