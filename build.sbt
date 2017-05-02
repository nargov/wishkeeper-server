val circeVersion = "0.7.0"
val dockerTestKitVersion = "0.9.0"
val logbackVersion = "1.2.1"
val specs2Version = "3.8.9"

val scalaVer = "2.11.8"

lazy val integrationSettings = inConfig(IntegrationTest)(Defaults.itSettings) ++ Seq(
  fork in IntegrationTest := false,
  parallelExecution in IntegrationTest := false
)

lazy val commonSettings = Seq(
  organization := "co.wishkeeper",
  scalaVersion := scalaVer,
  libraryDependencies ++= Seq(
    "com.google.guava" % "guava" % "19.0",
    "com.google.code.findbugs" % "jsr305" % "3.0.2",
    "joda-time" % "joda-time" % "2.9.7",
    "org.joda" % "joda-convert" % "1.8.1",
    "org.scalactic" %% "scalactic" % "3.0.1",
    "org.scalatest" %% "scalatest" % "3.0.1" % "test, it",
    "org.jmock" % "jmock-junit4" % "2.8.2" % Test,
    "org.jmock" % "jmock-legacy" % "2.8.2" % Test,
    "org.specs2" %% "specs2-core" % specs2Version % Test,
    "org.specs2" %% "specs2-matcher-extra" % specs2Version % Test,
    "com.wix" %% "specs2-jmock" % "1.0.0" % Test
  ),

  libraryDependencies ++= Seq(
    "ch.qos.logback" % "logback-core",
    "ch.qos.logback" % "logback-classic"
  ).map(_ % logbackVersion),
  scalacOptions in Test ++= Seq("-Yrangepos")
) ++ integrationSettings

lazy val wishkeeper = (project in file(".")).aggregate(server, testUtils, common).settings(
  scalaVersion := scalaVer
)

lazy val common = (project in file("wishkeeper-common")).
  configs(IntegrationTest).
  settings(
    commonSettings,
    integrationSettings,
    name := "wishkeeper-common",
    version := "1.0"
  )

lazy val server = (project in file("wishkeeper-server")).
  configs(IntegrationTest).
  settings(
    commonSettings,
    integrationSettings,
    name := "wishkeeper-server",
    version := "1.0",
    resolvers += Resolver.bintrayRepo("hseeberger", "maven"),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % "10.0.5",
      "com.typesafe.akka" %% "akka-http-testkit" % "10.0.5",
      "de.heikoseeberger" %% "akka-http-circe" % "1.12.0",
      "com.datastax.cassandra" % "cassandra-driver-core" % "3.2.0",
      "org.slf4j" % "slf4j-api" % "1.7.22",
      "io.appium" % "java-client" % "5.0.0-BETA6" % "it" exclude("com.codeborne", "phantomjsdriver")
    ),
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-parser"
    ).map(_ % circeVersion)
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
