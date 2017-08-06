package co.wishkeeper

import co.wishkeeper.server.CassandraDataStore
import com.datastax.driver.core.{Cluster, Session}
import org.scalatest.Matchers

class DataStoreTestHelper extends Matchers {

  def stop() = {
    session.close()
    cluster.close()
  }

  private var cluster: Cluster = _
  private var session: Session = _

  val nodeAddresses = "localhost" :: Nil

  def start() = {
    cluster = Cluster.builder().addContactPoints(nodeAddresses: _*).build()
    session = cluster.connect()
  }

  def createSchema(): Unit = {
    if(session == null) throw new IllegalStateException("DataStoreTestHelper not started. Did you forget to call start()?")
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