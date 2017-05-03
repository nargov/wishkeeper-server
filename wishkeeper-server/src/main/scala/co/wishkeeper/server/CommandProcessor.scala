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
        val userId = dataStore.userIdByFacebookId(connectUser.facebookId)
        val (user: User, lastSeqNum: Option[Long]) = userId.map(id =>
          (User.replay(dataStore.userEventsFor(id)), dataStore.lastSequenceNum(id))
        ).getOrElse((User.createNew(), None))
        val events = command.process(user)
        dataStore.saveUserEvents(user.id, lastSeqNum, now, events)
        dataStore.saveUserSession(user.id, connectUser.sessionId, now)
        events.foreach(event => eventProcessors.foreach(_.process(event)))

      case _ =>
        val userId = sessionId.flatMap(userIdForSession).getOrElse(throw new SessionNotFoundException(sessionId))
        val user = User.replay(dataStore.userEventsFor(userId))
        val events: Seq[UserEvent] = command.process(user)

        dataStore.saveUserEvents(userId, dataStore.lastSequenceNum(userId), DateTime.now(), events)
    }
  }

  //TODO Move this somewhere else - particular view, probably
  override def userIdForSession(sessionId: UUID): Option[UUID] = dataStore.userBySession(sessionId)
}

class SessionNotFoundException(sessionId: Option[UUID]) extends RuntimeException

trait EventProcessor {
  def process(event: Event): Unit
}

class UserIdByFacebookIdProjection(dataStore: DataStore) extends EventProcessor {
  val get: String => Option[UUID] = dataStore.userIdByFacebookId

  override def process(event: Event): Unit = event match {
    case UserFacebookIdSet(userId, facebookId) => dataStore.saveUserIdByFacebookId(facebookId, userId)
    case _ => //ignore all other events
  }
}

trait UserProfileProjection {
  def get(userId: UUID): UserProfile
}

class UserEventsUserProfileProjection(dataStore: DataStore) extends UserProfileProjection {
  def get(userId: UUID): UserProfile = {
    val events = dataStore.userEventsFor(userId)
    User.replay(events).userProfile
  }
}