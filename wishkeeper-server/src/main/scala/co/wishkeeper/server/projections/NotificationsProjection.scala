package co.wishkeeper.server.projections

import java.util.UUID
import java.util.UUID.randomUUID

import co.wishkeeper.server.CommandProcessor.retry
import co.wishkeeper.server.Events._
import co.wishkeeper.server.FriendRequestStatus.Approved
import co.wishkeeper.server.NotificationsData._
import co.wishkeeper.server.WishStatus.Deleted
import co.wishkeeper.server._
import org.joda.time.DateTime

trait NotificationsProjection extends EventProcessor {
  def notificationsFor(userId: UUID): List[Notification]
}

class DataStoreNotificationsProjection(dataStore: DataStore) extends NotificationsProjection {
  override def process[E <: UserEvent](instance: UserEventInstance[E]): List[UserEventInstance[_ <: UserEvent]] = instance.event match {
    case FriendRequestSent(sender, toUserId, id) => id.flatMap { requestId =>
      val newEvents = List(FriendRequestNotificationCreated(randomUUID(), toUserId, sender, requestId))
      val now = DateTime.now()
      retry {
        val lastSeqNum = dataStore.lastSequenceNum(toUserId)
        val result = dataStore.saveUserEvents(toUserId, lastSeqNum, now, newEvents)
        Either.cond(result, (), DbErrorEventsNotSaved)
      }.map(_ => UserEventInstance.list(toUserId, now, newEvents)).toOption
    }.getOrElse(Nil)
    case FriendRequestStatusChanged(_, reqId, from, status) if status == Approved =>
      val newEvents = List(FriendRequestAcceptedNotificationCreated(randomUUID(), from, instance.userId, reqId))
      val now = DateTime.now()
      retry {
        val lastSeqNum = dataStore.lastSequenceNum(from)
        val result = dataStore.saveUserEvents(from, lastSeqNum, now, newEvents)
        Either.cond(result, (), DbErrorEventsNotSaved)
      }.map(_ => UserEventInstance.list(from, now, newEvents)).getOrElse(Nil)
    case _ => Nil
  }

  def notificationsFor(userId: UUID): List[Notification] = {
    val user = User.replay(dataStore.userEvents(userId))
    val notifications = user.notifications
    notifications.filterNot(shouldBeDiscarded(user)).map(notification => notification.copy(data = notification.data match {
      case friendReq: FriendRequestNotification =>
        val sender = User.replay(dataStore.userEvents(friendReq.from))
        friendReq.withProfile(sender.userProfile)
      case accepted: FriendRequestAcceptedNotification =>
        val friend = User.replay(dataStore.userEvents(accepted.friendId))
        accepted.withProfile(friend.userProfile)
      case reserved: WishReservedNotification => reserved.copy(wishName = user.wishes(reserved.wishId).name)
      case unreserved: WishUnreservedNotification => unreserved.copy(wishName = user.wishes(unreserved.wishId).name)
      case x => x
    }))
  }

  private val shouldBeDiscarded: User => Notification => Boolean = user => {
    case Notification(_, wn: WishNotification, _, _) if user.wishes(wn.wishId).status == Deleted => true
    case _ => false
  }
}
