package co.wishkeeper.server

import java.nio.ByteBuffer
import java.util.UUID

import com.datastax.driver.core.policies.Policies
import com.datastax.driver.core.{Cluster, Session}
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import org.joda.time.DateTime
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

import scala.collection.JavaConverters._


class CassandraLearningTest extends FlatSpec with Matchers with BeforeAndAfterAll {

  case class User(id: UUID, created: DateTime, firstName: String, lastName: String)

  case class EventInstance(id: UUID, created: DateTime, sequenceNumber: Long, event: Event)

  sealed trait Event
  case class UserDidSomethingEvent(whatHeDo: String) extends Event
  case class UserDIdSomethingElseEvent(whatHeDo: String) extends Event

  val cluster = Cluster.builder().addContactPoint("localhost").withLoadBalancingPolicy(Policies.defaultLoadBalancingPolicy()).build()
  var session: Session = _

  override def beforeAll(): Unit = {
    CassandraDocker.start()
    session = cluster.connect()
  }

  override def afterAll(): Unit = {
    session.close()
    cluster.close()
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
        |seq bigint,
        |time timestamp,
        |event blob,
        |PRIMARY KEY((userId), seq)
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

  it should "insert and read multiple events" in {
    val userId = UUID.randomUUID()

    val events = (1 to 3).map(index ⇒ EventInstance(
      userId,
      DateTime.now(),
      index,
      UserDidSomethingEvent("something silly")))
    val insertEventStatement = session.prepare("insert into test.events (userId, time, seq, event) values (:userId, :time, :seq, :event)")
    val results = events.map(event ⇒ {
      val boundStatement = insertEventStatement.bind()
        .setUUID("userId", event.id)
        .setTimestamp("time", event.created.toDate)
        .setLong("seq", event.sequenceNumber)
        .setBytes("event", ByteBuffer.wrap(event.event.asJson.noSpaces.getBytes))
      session.execute(boundStatement)
    })
    results.forall(_.wasApplied) shouldBe true

    val resultSet = session.execute("select * from test.events")
    val readEvents = resultSet.asScala.map(row ⇒ {
      val eventStr = new String(row.getBytes("event").array())
      EventInstance(
        row.getUUID("userId"),
        new DateTime(row.getTimestamp("time")),
        row.getLong("seq"),
        decode[Event](eventStr).right.getOrElse(throw new IllegalStateException()))
    })

    readEvents.toStream should contain theSameElementsInOrderAs events
  }
}


