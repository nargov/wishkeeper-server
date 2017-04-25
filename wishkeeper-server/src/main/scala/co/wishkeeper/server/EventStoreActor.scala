package co.wishkeeper.server

import java.util.UUID

import akka.actor.Actor
import co.wishkeeper.server.EventStoreMessages._
import co.wishkeeper.server.Events.UserEvent
import org.joda.time.DateTime

import scala.util.Try

class EventStoreActor(eventStore: EventStore) extends Actor {

  override def receive: Receive = {
    case SaveUserEvents(userId, events) =>
      val response = Try {
        val lastSeq = eventStore.lastSequenceNum(userId)
        eventStore.saveUserEvents(userId, lastSeq, DateTime.now(), events)
        Saved
      }.get
      sender() ! response

    //TODO this doesn't belong here. Something to do with views
    case UpdateUserInfo(facebookId, userInfo) =>
      Try {
        eventStore.updateFacebookIdToUserInfo(facebookId, None, userInfo)
      }.get

    case UserInfoForFacebookId(id) =>
      val maybeUserInfoInstance: Option[UserInfoInstance] = Try {
        eventStore.userInfoByFacebookId(id)
      }.get

      sender() ! maybeUserInfoInstance.map(_.userInfo)

    case UserFromSession(session) =>
      val maybeUser: Option[UUID] = Try {
        eventStore.userBySession(session)
      }.get

      sender() ! maybeUser.map(id => User(id))

    case SaveUserSession(userId, sessionId) =>
      val trySaveUserSession = Try {
        eventStore.saveUserSession(userId, sessionId, DateTime.now())
      }

      if (trySaveUserSession.isSuccess) {
        sender() ! Saved
      }
  }
}

object EventStoreMessages {

  sealed trait EventStoreMessage

  case class SaveUserEvents(userId: UUID, events: Seq[UserEvent]) extends EventStoreMessage

  case object Saved extends EventStoreMessage

  case class UpdateUserInfo(facebookId: String, userInfo: UserInfo)

  case class UserInfoForFacebookId(facebookId: String)

  case class SaveUserSession(userId: UUID, sessionId: UUID)

  //  case class DataStoreError(e: Throwable) extends EventStoreMessage
}
