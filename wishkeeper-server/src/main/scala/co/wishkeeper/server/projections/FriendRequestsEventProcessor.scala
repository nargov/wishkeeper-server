package co.wishkeeper.server.projections

import java.util.UUID

import co.wishkeeper.server.CommandProcessor.retry
import co.wishkeeper.server.Events._
import co.wishkeeper.server.{DataStore, DbErrorEventsNotSaved, Error, EventProcessor, User}
import org.joda.time.DateTime


class FriendRequestsEventProcessor(dataStore: DataStore) extends EventProcessor {
  override def process(event: Event, userId: UUID): List[(UUID, Event)] = event match {
    case FriendRequestStatusChanged(_, requestId, from, status) =>
      val friendRequestStatusChanged = FriendRequestStatusChanged(from, requestId, from, status)
      saveEventFor(from, friendRequestStatusChanged)
      Nil
    case FriendRemoved(_, friendId) =>
      val removed = FriendRemoved(friendId, userId)
      val friend = User.replay(dataStore.userEvents(friendId))
      if(friend.hasFriend(userId)){
        saveEventFor(friendId, removed)
        (friendId, removed) :: Nil
      }
      else Nil
    case FriendRequestSent(sender, receiver, id) => id.flatMap { _ =>
      val newEvents = List(FriendRequestReceived(receiver, sender, id))
      saveEventFor(receiver, newEvents.head).map(_ => newEvents.map((receiver, _))).toOption
    }.getOrElse(Nil)
    case _ => Nil
  }

  private def saveEventFor(userId: UUID, event: UserEvent): Either[Error, Unit] = retry {
    Either.cond(dataStore.saveUserEvents(userId, dataStore.lastSequenceNum(userId), DateTime.now(), List(event)), (), DbErrorEventsNotSaved)
  }
}
