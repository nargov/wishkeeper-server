val circeVersion = "0.8.0"
val dockerTestKitVersion = "0.9.0"
val logbackVersion = "1.2.1"
val specs2Version = "3.9.0"

val scalaVer = "2.11.8"

val artifactsDir = "/home/nimrod/dev/wishkeeper-artifacts"

lazy val integrationSettings = inConfig(IntegrationTest)(Defaults.itSettings) ++ Seq(
  fork in IntegrationTest := false,
  parallelExecution in IntegrationTest := false
)

lazy val commonSettings = Seq(
  version := "1.0.5",
  organization := "co.wishkeeper",
  scalaVersion := scalaVer,
  scalacOptions ++= Seq("-deprecation", "-feature"),

  resolvers +=
    "Artifactory" at "http://ci-artifacts.wishkeeper.co:8081/artifactory/sbt-release/",

  libraryDependencies ++= Seq(
    "com.google.guava" % "guava" % "19.0",
    "com.fasterxml.jackson.core" % "jackson-core" % "2.6.0",
    "joda-time" % "joda-time" % "2.9.7",
    "org.joda" % "joda-convert" % "1.8.1",
    "org.scalactic" %% "scalactic" % "3.0.1",
    "org.scalatest" %% "scalatest" % "3.0.1" % "test, it",
    "org.jmock" % "jmock-junit4" % "2.8.2" % Test,
    "org.jmock" % "jmock-legacy" % "2.8.2" % Test,
    "org.specs2" %% "specs2-core" % specs2Version % "test, it",
    "org.specs2" %% "specs2-matcher-extra" % specs2Version % "test, it",
    "com.wix" %% "specs2-jmock" % "1.0.0" % Test
  ),

  libraryDependencies += "org.typelevel" %% "cats" % "0.9.0",

  libraryDependencies ++= Seq(
    "io.circe" %% "circe-core",
    "io.circe" %% "circe-generic",
    "io.circe" %% "circe-parser",
    "io.circe" %% "circe-generic-extras"
  ).map(_ % circeVersion),

  libraryDependencies ++= Seq(
    "ch.qos.logback" % "logback-core",
    "ch.qos.logback" % "logback-classic"
  ).map(_ % logbackVersion),
  scalacOptions in Test ++= Seq("-Yrangepos"),

  publishTo := Some("Artifactory Realm" at "http://ci-artifacts.wishkeeper.co:8081/artifactory/sbt-release")

) ++ integrationSettings

lazy val wishkeeper = (project in file(".")).aggregate(server, testUtils, common).settings(
  scalaVersion := scalaVer,
  publish := {},
  publishLocal := {}
)

lazy val common = (project in file("wishkeeper-common")).
  configs(IntegrationTest).
  settings(
    commonSettings,
    integrationSettings,
    name := "wishkeeper-common"
  )

lazy val server = (project in file("wishkeeper-server")).
  configs(IntegrationTest).
  settings(
    commonSettings,
    integrationSettings,
    name := "wishkeeper-server",
    resolvers += Resolver.bintrayRepo("hseeberger", "maven"),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % "10.0.5",
      "com.typesafe.akka" %% "akka-http-testkit" % "10.0.5",
      "de.heikoseeberger" %% "akka-http-circe" % "1.12.0",
      "com.datastax.cassandra" % "cassandra-driver-core" % "3.2.0",
      "org.slf4j" % "slf4j-api" % "1.7.22",
      "commons-io" % "commons-io" % "2.5",
      "com.google.api-client" % "google-api-client" % "1.22.0"
        exclude("org.apache.httpcomponents", "httpclient")
        exclude("com.google.guava", "guava-jdk5"),
      "com.google.cloud" % "google-cloud-storage" % "1.0.2"
        exclude("org.apache.httpcomponents", "httpclient")
        exclude("com.google.guava", "guava-jdk5"),
      "com.sksamuel.scrimage" %% "scrimage-core" % "2.1.7"
        exclude("commons-io", "commons-io"),
      "io.appium" % "java-client" % "5.0.0-BETA6" % "it" exclude("com.codeborne", "phantomjsdriver")
    ),
    packAutoSettings,
    addArtifact(Artifact("wishkeeper-server", "Bundled Archive", "tar.gz"), packArchiveTgz).settings
  ).
  dependsOn(common, testUtils % "it->compile")

lazy val testUtils = (project in file("test-utils")).
  configs(IntegrationTest).
  settings(
    commonSettings,
    integrationSettings,
    libraryDependencies ++= Seq(
      "com.spotify" % "docker-client" % "8.3.1"
    )
  )

conflictManager := ConflictManager.latestCompatible