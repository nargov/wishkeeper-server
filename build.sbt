val circeVersion = "0.10.1"
val dockerTestKitVersion = "0.9.0"
val logbackVersion = "1.2.1"
val specs2Version = "4.2.0"

val scalaVer = "2.12.7"

lazy val integrationSettings = inConfig(IntegrationTest)(Defaults.itSettings) ++ Seq(
  fork in IntegrationTest := false,
  parallelExecution in IntegrationTest := false
)

lazy val artifactory = Some("Artifactory Realm" at "http://ci-artifacts.wishkeeper.co:8081/artifactory/sbt-release")

lazy val commonSettings = Seq(
  organization := "co.wishkeeper",
  scalaVersion := scalaVer,
  scalacOptions ++= Seq("-deprecation", "-feature", "-Ypartial-unification"),
  sources in (Compile,doc) := Seq.empty,
  publishArtifact in (Compile, packageDoc) := false,
  
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
    "com.wix" %% "specs2-jmock" % "1.2.0" % Test
  ),

  libraryDependencies += "org.typelevel" %% "cats-core" % "1.3.1",

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

  publishTo := artifactory

) ++ integrationSettings

lazy val wishkeeper = (project in file(".")).aggregate(server, testUtils, common).settings(
  scalaVersion := scalaVer,
  publish := {},
  publishLocal := {},
  publishTo := artifactory
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
      "com.typesafe.akka" %% "akka-http" % "10.1.5",
      "com.typesafe.akka" %% "akka-http-testkit" % "10.1.5",
      "de.heikoseeberger" %% "akka-http-circe" % "1.22.0",
      "com.datastax.cassandra" % "cassandra-driver-core" % "3.2.0",
      "org.slf4j" % "slf4j-api" % "1.7.22",
      "commons-io" % "commons-io" % "2.5",
      "com.google.api-client" % "google-api-client" % "1.22.0"
        exclude("org.apache.httpcomponents", "httpclient")
        exclude("com.google.guava", "guava-jdk5"),
      "com.google.cloud" % "google-cloud-storage" % "1.14.0"
        exclude("org.apache.httpcomponents", "httpclient")
        exclude("com.google.guava", "guava-jdk5"),
      "com.google.cloud" % "google-cloud-pubsub" % "1.31.0",
      "com.google.apis" % "google-api-services-people" % "v1-rev352-1.25.0",
      "com.google.firebase" % "firebase-admin" % "6.2.0",
      "com.sksamuel.scrimage" %% "scrimage-core" % "2.1.8"
        exclude("commons-io", "commons-io"),
      "io.appium" % "java-client" % "5.0.0-BETA6" % "it" exclude("com.codeborne", "phantomjsdriver"),
      "org.scalatra.scalate" %% "scalate-core" % "1.9.0"
    ),
    packAutoSettings,
    addArtifact(Artifact("wishkeeper-server", "Bundled Archive", "tar.gz"), packArchiveTgz).settings
  ).
  dependsOn(common % "compile;test->test;it->test", testUtils % "it->compile")

lazy val testUtils = (project in file("test-utils")).
  configs(IntegrationTest).
  settings(
    commonSettings,
    integrationSettings,
    libraryDependencies ++= Seq(
      "com.spotify" % "docker-client" % "8.3.1",
      "org.specs2" %% "specs2-core" % specs2Version,
      "org.specs2" %% "specs2-matcher-extra" % specs2Version,
      "com.typesafe.akka" %% "akka-http" % "10.0.5"
    )
  ).dependsOn(common % "compile;test->test;it->test")

conflictManager := ConflictManager.latestCompatible