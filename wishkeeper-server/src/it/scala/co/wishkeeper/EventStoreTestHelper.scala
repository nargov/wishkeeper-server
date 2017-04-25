package co.wishkeeper

import co.wishkeeper.server.CassandraEventStore
import com.datastax.driver.core.{Cluster, Session}
import org.scalatest.Matchers

class EventStoreTestHelper extends Matchers {
  val cluster = Cluster.builder().addContactPoint("localhost").build()
  val session: Session = cluster.connect()

  def createSchema(): Unit = {
    session.execute("create keyspace if not exists wishkeeper with replication = {'class': 'SimpleStrategy', 'replication_factor': 1}")
      .wasApplied() shouldBe true

    session.execute(
      s"""
        |create table if not exists ${CassandraEventStore.userEventsTable} (
        | userId UUID,
        | seq bigint,
        | seqMax bigint STATIC,
        | time timestamp,
        | event blob,
        | PRIMARY KEY((userId), seq)
        |)
      """.stripMargin).wasApplied() shouldBe true

    session.execute(
      s"""
        |create table if not exists ${CassandraEventStore.userInfoByFacebookIdTable} (
        | facebookId text,
        | seq bigint,
        | userInfo blob,
        | PRIMARY KEY(facebookId)
        |)
      """.stripMargin
    )

    session.execute(
      s"""
        |create table if not exists ${CassandraEventStore.userSession} (
        | sessionId UUID,
        | userId UUID,
        | created timestamp,
        | PRIMARY KEY(sessionId)
        |)
      """.stripMargin
    )
  }
}
object EventStoreTestHelper{
  def apply() = new EventStoreTestHelper
}