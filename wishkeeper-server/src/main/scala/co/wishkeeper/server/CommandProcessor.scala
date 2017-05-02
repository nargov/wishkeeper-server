package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.Commands.{ConnectFacebookUser, SetFacebookUserInfo, UserCommand}
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
        val user = User.createNew()
        val events = command.process(user)
        val lastSeqNum = dataStore.lastSequenceNum(user.id)
        val now = DateTime.now()
        dataStore.saveUserEvents(user.id, lastSeqNum, now, events)
        dataStore.saveUserSession(user.id, connectUser.sessionId, now)
        events.foreach(event => eventProcessors.foreach(_.process(event)))

      case _: SetFacebookUserInfo =>
        val userId = sessionId.flatMap(userIdForSession).getOrElse(throw new SessionNotFoundException(sessionId))
        val events: Seq[UserEvent] = command.process(User(userId)) //TODO replace with user loaded from datastore

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