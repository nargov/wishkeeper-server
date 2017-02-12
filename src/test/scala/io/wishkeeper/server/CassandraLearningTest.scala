package io.wishkeeper.server

import java.util.UUID

import com.datastax.driver.core.{Cluster, Session}
import com.whisk.docker.impl.spotify.DockerKitSpotify
import com.whisk.docker.scalatest.DockerTestKit
import com.whisk.docker.{DockerContainer, DockerKit, DockerReadyChecker}
import org.joda.time.DateTime
import org.scalatest.{FlatSpec, Matchers}
import scala.collection.JavaConverters._

class CassandraLearningTest extends FlatSpec with DockerTestKit with DockerKitSpotify with CassandraDocker with Matchers {

  val cluster = Cluster.builder().addContactPoint("localhost").build()
  var session: Session = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    session = cluster.connect()
  }

  override def afterAll(): Unit = {
    session.close()
    super.afterAll()
  }

  it should "create a keyspace" in {
    val resultSet = session.execute("create keyspace if not exists test with replication = {'class': 'SimpleStrategy', 'replication_factor': 1}")
    resultSet.wasApplied() shouldBe true
  }

  it should "create a simple table" in {
    val createTable = session.execute(
      """create table if not exists test.users(
        |userId UUID PRIMARY KEY,
        |timeCreated timestamp,
        |firstName text,
        |lastName text)
      """.stripMargin)
    createTable.wasApplied() shouldBe true
  }

  it should "create a table with compound primary key" in {
    val createTable = session.execute(
      """
        |create table if not exists test.events(
        |userId UUID,
        |time timestamp,
        |seq int,
        |event blob,
        |PRIMARY KEY(userId, time, seq)
        |)
      """.stripMargin)
    createTable.wasApplied() shouldBe true
  }

  it should "insert and read user" in {
    val expectedUser = User(UUID.randomUUID(), DateTime.now(), "Nimrod", "Argov")
    val boundStatement = session.prepare(s"insert into test.users (userId, timeCreated, firstName, lastName) values (:userId, :timeCreated, :firstName, :lastName)").bind()
    boundStatement.setUUID("userId", expectedUser.id)
    boundStatement.setTimestamp("timeCreated", expectedUser.created.toDate)
    boundStatement.setString("firstName", expectedUser.firstName)
    boundStatement.setString("lastName", expectedUser.lastName)
    session.execute(boundStatement).wasApplied() shouldBe true

    val resultSet = session.execute("select * from test.users")
    val users = resultSet.asScala.map(row ⇒ User(
      row.getUUID("userId"),
      new DateTime(row.getTimestamp("timeCreated")),
      row.getString("firstName"),
      row.getString("lastName")))
    users should have size 1
    users.head shouldEqual expectedUser

  }
}

case class User(id: UUID, created: DateTime, firstName: String, lastName: String)

case class Event(id: UUID, created: DateTime, eventType: String, eventData: String)

trait CassandraDocker extends DockerKit {
  val DefaultCqlPort = 9042

  val cassandraContainer: DockerContainer = DockerContainer("cassandra:3.9")
    .withPorts(DefaultCqlPort -> Some(DefaultCqlPort))
    .withReadyChecker(DockerReadyChecker.LogLineContains("Starting listening for CQL clients on"))

  abstract override def dockerContainers: List[DockerContainer] = cassandraContainer :: super.dockerContainers
}