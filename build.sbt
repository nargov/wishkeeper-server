val circeVersion = "0.7.0"
val dockerTestKitVersion = "0.9.0"
val logbackVersion = "1.2.1"

//TODO find out how to implement this
lazy val testAll = TaskKey[Unit]("test-all")

lazy val integrationSettings = Defaults.itSettings ++ Seq(
  fork in IntegrationTest := false,
  parallelExecution in IntegrationTest := false
)

lazy val commonSettings = Seq(
  organization := "co.wishkeeper",
  scalaVersion := "2.11.8",
  libraryDependencies ++= Seq(
    "com.google.guava" % "guava" % "18.0",
    "com.google.code.findbugs" % "jsr305" % "3.0.2",
    "org.scalactic" %% "scalactic" % "3.0.1",
    "org.scalatest" %% "scalatest" % "3.0.1" % "test, it",
    "com.spotify" % "docker-client" % "8.3.1" % "test, it"
  ),

  libraryDependencies ++= Seq(
    "ch.qos.logback" % "logback-core",
    "ch.qos.logback" % "logback-classic"
  ).map(_ % logbackVersion)
) ++ integrationSettings

lazy val wishkeeper = (project in file(".")).aggregate(server, e2e)

lazy val server = (project in file("wishkeeper-server")).
  configs(IntegrationTest).
  settings(
    commonSettings,
    name := "wishkeeper-server",
    version := "1.0",
    resolvers += Resolver.bintrayRepo("hseeberger", "maven"),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % "10.0.3",
      "de.heikoseeberger" %% "akka-http-circe" % "1.12.0",
      "joda-time" % "joda-time" % "2.9.7",
      "org.joda" % "joda-convert" % "1.8.1",
      "com.datastax.cassandra" % "cassandra-driver-core" % "3.1.4",
      "org.slf4j" % "slf4j-api" % "1.7.22"),

    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-parser"
    ).map(_ % circeVersion)
  )

lazy val e2e = (project in file("wishkeeper-e2e")).
  configs(IntegrationTest).
  settings(
    commonSettings,
    name := "wishkeeper-e2e",
    version := "1.0.0",
    scalaVersion := "2.11.8",
    libraryDependencies ++= Seq(
      "io.appium" % "java-client" % "5.0.0-BETA6" % "it"
    )
  ).dependsOn(server)
