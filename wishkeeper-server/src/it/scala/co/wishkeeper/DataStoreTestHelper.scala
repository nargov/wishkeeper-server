package co.wishkeeper

import co.wishkeeper.server.CassandraDataStore
import com.datastax.driver.core.{Cluster, Session}
import org.scalatest.Matchers

class DataStoreTestHelper extends Matchers {
  val cluster = Cluster.builder().addContactPoint("localhost").build()
  val session: Session = cluster.connect()

  def createSchema(): Unit = {
    session.execute("create keyspace if not exists wishkeeper with replication = {'class': 'SimpleStrategy', 'replication_factor': 1}")
      .wasApplied() shouldBe true

    session.execute(
      s"""
         |create table if not exists ${CassandraDataStore.userEventsTable} (
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
         |create table if not exists ${CassandraDataStore.userByFacebookId} (
         |  facebookId text,
         |  userId UUID,
         |  PRIMARY KEY(facebookId)
         |)
       """.stripMargin
    )

    session.execute(
      s"""
         |create table if not exists ${CassandraDataStore.userSession} (
         | sessionId UUID,
         | userId UUID,
         | created timestamp,
         | PRIMARY KEY(sessionId)
         |)
      """.stripMargin
    )
  }
}

object DataStoreTestHelper {
  def apply() = new DataStoreTestHelper
}