package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.Commands.{ConnectFacebookUser, UserCommand}
import co.wishkeeper.server.Events._
import org.joda.time.DateTime

trait CommandProcessor {
  def process(command: UserCommand, sessionId: Option[UUID] = None): Unit

  def userIdForSession(sessionId: UUID): Option[UUID]
}

class UserCommandProcessor(dataStore: DataStore, eventProcessors: List[EventProcessor] = Nil) extends CommandProcessor {

  //TODO unify as much as possible
  override def process(command: UserCommand, sessionId: Option[UUID]): Unit = {
    command match {
      case connectUser: ConnectFacebookUser =>
        val now = DateTime.now()
        /* FIXME
           This check is not good enough since the save and read can be interleaved, creating two users with the same facebook id.
           Prevent this by saving into userIdByFacebookId with IF NOT EXISTS before saving the events. */
        val userId = dataStore.userIdByFacebookId(connectUser.facebookId)
        val (user: User, lastSeqNum: Option[Long]) = userId.map(id =>
          (User.replay(dataStore.userEventsFor(id)), dataStore.lastSequenceNum(id))
        ).getOrElse((User.createNew(), None))
        val events = command.process(user)
        dataStore.saveUserEvents(user.id, lastSeqNum, now, events)
        events.foreach(event => eventProcessors.foreach(_.process(event)))
        dataStore.saveUserSession(user.id, connectUser.sessionId, now)

      case _ =>
        val userId = sessionId.flatMap(userIdForSession).getOrElse(throw new SessionNotFoundException(sessionId))
        val user = User.replay(dataStore.userEventsFor(userId))
        val events: Seq[UserEvent] = command.process(user)

        dataStore.saveUserEvents(userId, dataStore.lastSequenceNum(userId), DateTime.now(), events)
        events.foreach(event => eventProcessors.foreach(_.process(event)))
    }
  }

  //TODO Move this somewhere else - particular view, probably
  override def userIdForSession(sessionId: UUID): Option[UUID] = dataStore.userBySession(sessionId)
}

trait EventProcessor {
  def process(event: Event): Unit
}

trait IncomingFriendRequestsProjection {
  def awaitingApproval(userId: UUID): List[UUID]
}

class DataStoreIncomingFriendRequestsProjection(dataStore: DataStore) extends IncomingFriendRequestsProjection with EventProcessor {
  def awaitingApproval(userId: UUID): List[UUID] = User.replay(dataStore.userEventsFor(userId)).friends.requestReceived

  override def process(event: Event): Unit = { event match {
    case FriendRequestSent(sender, userId) =>
      val lastSequenceNum = dataStore.lastSequenceNum(userId)
      dataStore.saveUserEvents(userId, lastSequenceNum, DateTime.now(), FriendRequestReceived(userId, sender) :: Nil)
    case _ =>
  }}
}

class SessionNotFoundException(sessionId: Option[UUID]) extends RuntimeException(
  sessionId.map(id => s"Session ${id.toString} not found.").getOrElse("Session not found"))

case class PotentialFriend(userId: UUID, name: String, image: String, requestSent: Boolean = false)