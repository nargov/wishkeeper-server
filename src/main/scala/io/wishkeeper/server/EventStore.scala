package io.wishkeeper.server

import java.util.UUID

import com.datastax.driver.core.Cluster
import org.joda.time.DateTime


trait EventStore {
  def persistUserEvent(userId: UUID, lastSequenceNumber: Long, time: DateTime, event: UserEvent)
  def lastSequenceNum(): Long
}

class CassandraEventStore extends EventStore {
  val cluster = Cluster.builder().addContactPoint("localhost").build()
  val session = cluster.connect()
  private val userEvents = "wishkeeper.user_events"
  private val insertEventStatement = session.prepare(s"insert into $userEvents (userId, seq, time, event) values (:userId, :seq, :time, :event)")

  override def persistUserEvent(userId: UUID, lastSequenceNumber: Long, time: DateTime, event: UserEvent): Unit = {
    val insert = insertEventStatement.bind().setUUID("userId", userId).setLong("seq", lastSequenceNumber + 1).setTimestamp("time", time.toDate)
    session.execute(insert)
  }

  override def lastSequenceNum(): Long = {
    val resultSet = session.execute(s"select seq from $userEvents limit 1")
    resultSet.one().getLong(0)
  }
}

