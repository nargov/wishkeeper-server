package io.wishkeeper.server

import java.util.UUID

import akka.actor.Actor
import io.wishkeeper.server.EventStoreMessages._
import io.wishkeeper.server.Events.UserEvent
import org.joda.time.DateTime

import scala.util.Try

class EventStoreActor(eventStore: EventStore) extends Actor {

  override def receive: Receive = {

    case PersistUserEvent(userId, event) ⇒
      val response = Try {
        val lastSeq = eventStore.lastSequenceNum()
        eventStore.persistUserEvent(userId, lastSeq, DateTime.now(), event) //TODO add unit test that simulates exception here
        Persisted
      }
        //        .recover { case err: Exception ⇒
        //        DataStoreError(err)
        //      }
        .get
      sender() ! response

    //    case UserWishesFor(userId) ⇒
    //      val response = Try {
    //        Persisted
    //      }.recover { case err: Exception ⇒
    //        DataStoreError(err)
    //      }.get
    //      sender() ! response
  }
}

object EventStoreMessages {

  sealed trait EventStoreMessage

  case class PersistUserEvent(userId: UUID, event: UserEvent) extends EventStoreMessage

  case object Persisted extends EventStoreMessage

//  case class DataStoreError(e: Throwable) extends EventStoreMessage

//  case class UserWishesFor(userId: UUID) extends EventStoreMessage

}
