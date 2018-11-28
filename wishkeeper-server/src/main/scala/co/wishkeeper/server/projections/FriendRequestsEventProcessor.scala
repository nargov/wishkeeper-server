package co.wishkeeper.server.projections

import java.util.UUID

import co.wishkeeper.server.CommandProcessor.retry
import co.wishkeeper.server.Events._
import co.wishkeeper.server.{DataStore, DbErrorEventsNotSaved, Error, EventProcessor, User, UserEventInstance}
import org.joda.time.DateTime


class FriendRequestsEventProcessor(dataStore: DataStore) extends EventProcessor {
  override def process[E <: UserEvent](instance: UserEventInstance[E]): List[UserEventInstance[_ <: UserEvent]] = instance.event match {
    case FriendRequestStatusChanged(_, requestId, from, status) =>
      val friendRequestStatusChanged = FriendRequestStatusChanged(from, requestId, from, status)
      saveEventFor(from, friendRequestStatusChanged, DateTime.now())
      Nil
    case FriendRemoved(_, friendId) =>
      val removed = FriendRemoved(friendId, instance.userId)
      val friend = User.replay(dataStore.userEvents(friendId))
      if(friend.hasFriend(instance.userId)){
        val now = DateTime.now()
        saveEventFor(friendId, removed, now)
        UserEventInstance(friendId, removed, now) :: Nil
      }
      else Nil
    case FriendRequestSent(sender, receiver, id) => id.flatMap { _ =>
      val newEvents = List(FriendRequestReceived(receiver, sender, id))
      val now = DateTime.now()
      saveEventFor(receiver, newEvents.head, now).map(_ => UserEventInstance.list(receiver, now, newEvents)).toOption
    }.getOrElse(Nil)
    case _ => Nil
  }

  private def saveEventFor(userId: UUID, event: UserEvent, time: DateTime): Either[Error, Unit] = retry {
    Either.cond(dataStore.saveUserEvents(userId, dataStore.lastSequenceNum(userId), time, List(event)), (), DbErrorEventsNotSaved)
  }
}
