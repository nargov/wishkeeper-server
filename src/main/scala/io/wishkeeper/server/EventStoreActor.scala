package io.wishkeeper.server

import java.util.UUID

import akka.actor.Actor
import io.wishkeeper.server.EventStoreMessages.{DataStoreError, PersistUserEvent, Persisted}
import org.joda.time.DateTime

import scala.util.Try

class EventStoreActor(eventStore: EventStore) extends Actor {

  override def receive: Receive = {
    case PersistUserEvent(userId, event) ⇒
      val response = Try {
        val lastSeq = eventStore.lastSequenceNum()
        eventStore.persistUserEvent(userId, lastSeq, DateTime.now(), event) //TODO add unit test that simulates exception here
        Persisted
      }.recover { case err: Exception ⇒
        DataStoreError(err)
      }.get
      sender() ! response
  }
}

object EventStoreMessages {

  case class PersistUserEvent(userId: UUID, event: UserEvent)

  case object Persisted

  case class DataStoreError(e: Throwable)
}
