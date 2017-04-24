package co.wishkeeper.server

import java.util.UUID

import akka.actor.Actor
import co.wishkeeper.server.EventStoreMessages._
import co.wishkeeper.server.Events.UserEvent
import org.joda.time.DateTime

import scala.util.Try

class EventStoreActor(eventStore: EventStore) extends Actor {

  override def receive: Receive = {
    case PersistUserEvents(userId, events) ⇒
      val response = Try {
        val lastSeq = eventStore.lastSequenceNum(userId)
        eventStore.persistUserEvents(userId, lastSeq, DateTime.now(), events)
        Persisted
      }.get
      sender() ! response

    //TODO this doesn't belong here. Something to do with views
    case UpdateUserInfo(facebookId, userInfo) ⇒
      Try {
        eventStore.updateFacebookIdToUserInfo(facebookId, None, userInfo)
      }.get

    case UserInfoForFacebookId(id) ⇒
      val maybeUserInfoInstance: Option[UserInfoInstance] = Try {
        eventStore.userInfoByFacebookId(id)
      }.get

      sender() ! maybeUserInfoInstance.map(_.userInfo)
  }
}

object EventStoreMessages {

  sealed trait EventStoreMessage

  case class PersistUserEvents(userId: UUID, events: Seq[UserEvent]) extends EventStoreMessage

  case object Persisted extends EventStoreMessage

  case class UpdateUserInfo(facebookId: String, userInfo: UserInfo)

  case class UserInfoForFacebookId(facebookId: String)

  //  case class DataStoreError(e: Throwable) extends EventStoreMessage

  //  case class UserWishesFor(userId: UUID) extends EventStoreMessage

}
