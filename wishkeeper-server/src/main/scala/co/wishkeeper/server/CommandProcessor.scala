package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.CommandProcessor.retry
import co.wishkeeper.server.user.commands.{ConnectFacebookUser, UserCommand, UserCommandValidator}
import co.wishkeeper.server.Events._
import org.joda.time.DateTime

import scala.annotation.tailrec

trait CommandProcessor {
  def process(command: UserCommand, sessionId: Option[UUID] = None): Boolean

  def process(command: UserCommand, userId: UUID): Either[Error, Unit]

  def validatedProcess[C <: UserCommand](command: C, userId: UUID)(implicit validator: UserCommandValidator[C]): Either[Error, Unit]
}

class UserCommandProcessor(dataStore: DataStore, eventProcessors: List[EventProcessor] = Nil) extends CommandProcessor {

  //TODO unify as much as possible
  override def process(command: UserCommand, sessionId: Option[UUID]): Boolean = {
    command match {
      case connectUser: ConnectFacebookUser =>
        val now = DateTime.now()
        /* FIXME
           This check is not good enough since the save and read can be interleaved, creating two users with the same facebook id.
           Prevent this by saving into userIdByFacebookId with IF NOT EXISTS before saving the events. */
        val userId = dataStore.userIdByFacebookId(connectUser.facebookId)
        val (user: User, lastSeqNum: Option[Long]) = userId.map(id =>
          (User.replay(dataStore.userEvents(id)), dataStore.lastSequenceNum(id))
        ).getOrElse((User.createNew(), None))
        val savedSession = dataStore.saveUserSession(user.id, connectUser.sessionId, now)
        val events = command.process(user)
        val savedEvents = dataStore.saveUserEvents(user.id, lastSeqNum, now, events)
        events.foreach(event => eventProcessors.foreach(_.process(event)))
        savedEvents && savedSession

      case _ =>
        val userId = sessionId.flatMap(dataStore.userBySession).getOrElse(throw new SessionNotFoundException(sessionId))
        process(command, userId)
        true
    }
  }

  override def process(command: UserCommand, userId: UUID): Either[Error, Unit] = {
    retry {
      val lastSeqNum = dataStore.lastSequenceNum(userId)
      val user = User.replay(dataStore.userEvents(userId))
      val events: Seq[UserEvent] = command.process(user)
      val success = dataStore.saveUserEvents(userId, lastSeqNum, DateTime.now(), events)
      if (success)
        events.foreach(event => eventProcessors.foreach(_.process(event)))
      Either.cond(success, (), DbErrorEventsNotSaved)
    }
  }

  override def validatedProcess[C <: UserCommand](command: C, userId: UUID)(implicit validator: UserCommandValidator[C]): Either[Error, Unit] =
    retry {
      val lastSeqNum = dataStore.lastSequenceNum(userId)
      val user = User.replay(dataStore.userEvents(userId))
      validator.validate(user, command).flatMap { _ =>
        val events: Seq[UserEvent] = command.process(user)
        val success = dataStore.saveUserEvents(userId, lastSeqNum, DateTime.now(), events)
        if (success)
          events.foreach(event => eventProcessors.foreach(_.process(event)))
        Either.cond(success, (), DbErrorEventsNotSaved)
      }
    }
}

object CommandProcessor {
  @tailrec
  def retry(f: => Either[Error, Unit], retries: Int = 50): Either[Error, Unit] = {
    val result = f
    result match {
      case Left(DbErrorEventsNotSaved) if retries > 0 => retry(f, retries - 1)
      case _ => result
    }
  }
}

trait EventProcessor {
  def process(event: Event): Unit
}

class UserByEmailProjection(dataStore: DataStore) extends EventProcessor {

  override def process(event: Event): Unit = {
    event match {
      case UserEmailSet(userId, email) => dataStore.saveUserByEmail(email, userId)
      case _ =>
    }
  }
}
