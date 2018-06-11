package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.CommandProcessor.retry
import co.wishkeeper.server.Events._
import co.wishkeeper.server.user.commands.{ConnectFacebookUser, UserCommand, UserCommandValidator}
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
        if (savedEvents) publishEvents(events, user.id)
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
      val events: List[UserEvent] = command.process(user)
      val success = dataStore.saveUserEvents(userId, lastSeqNum, DateTime.now(), events)
      if (success) publishEvents(events, user.id)
      Either.cond(success, (), DbErrorEventsNotSaved)
    }
  }

  private def publishEvents(events: List[Event], userId: UUID): Unit = {
    println("Publishing events: " + events.mkString)
    events.foreach(publishEvent(_, userId))
  }

  private def publishEvent(event: Event, userId: UUID): Unit = eventProcessors.foreach(processor =>
    processor.process(event, userId).foreach(newEvent => publishEvent(newEvent._2, newEvent._1)))

  override def validatedProcess[C <: UserCommand](command: C, userId: UUID)(implicit validator: UserCommandValidator[C]): Either[Error, Unit] =
    retry {
      val lastSeqNum = dataStore.lastSequenceNum(userId)
      val user = User.replay(dataStore.userEvents(userId))
      validator.validate(user, command).flatMap { _ =>
        val events: List[UserEvent] = command.process(user)
        val success = dataStore.saveUserEvents(userId, lastSeqNum, DateTime.now(), events)
        if (success) publishEvents(events, user.id)
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
  def process(event: Event, userId: UUID): List[(UUID, Event)] = Nil
}

class UserByEmailProjection(dataStore: DataStore) extends EventProcessor {
  override def process(event: Event, userId: UUID): List[(UUID, Event)] = {
    event match {
      case UserEmailSet(_, email) => dataStore.saveUserByEmail(email, userId)
      case _ =>
    }
    Nil
  }
}
