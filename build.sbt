lazy val root = (project in file(".")).settings(
  name := "wishkeeper-server",
  version := "1.0",
  scalaVersion := "2.11.8",
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-http" % "10.0.3",
    "com.typesafe.akka" %% "akka-http-spray-json" % "10.0.3",
    "joda-time" % "joda-time" % "2.9.7",
    "com.datastax.cassandra" % "cassandra-driver-core" % "3.1.3",
    "org.scalactic" %% "scalactic" % "3.0.1",
    "ch.qos.logback" % "logback-core" % "1.2.1",
    "ch.qos.logback" % "logback-classic" % "1.2.1",
    "org.slf4j" % "slf4j-api" % "1.7.22",
    "org.scalatest" %% "scalatest" % "3.0.1" % "test",
    "com.whisk" %% "docker-testkit-scalatest" % "0.9.0" % "test",
    "com.whisk" %% "docker-testkit-impl-spotify" % "0.9.0" % "test")
)