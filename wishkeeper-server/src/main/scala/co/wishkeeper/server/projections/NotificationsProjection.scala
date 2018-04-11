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
  override def process(event: Event): Unit = event match {
    case FriendRequestSent(sender, userId, id) => id.foreach { requestId =>
      retry {
        val lastSeqNum = dataStore.lastSequenceNum(userId)
        val result = dataStore.saveUserEvents(userId, lastSeqNum, DateTime.now(), List(
          FriendRequestNotificationCreated(randomUUID(), userId, sender, requestId)
        ))
        Either.cond(result, (), DbErrorEventsNotSaved)
      }
    }
    case FriendRequestStatusChanged(userId, reqId, from, status) if status == Approved =>
      retry {
        val lastSeqNum = dataStore.lastSequenceNum(from)
        val result = dataStore.saveUserEvents(from, lastSeqNum, DateTime.now(), List(
          FriendRequestAcceptedNotificationCreated(randomUUID(), from, userId, reqId)
        ))
        Either.cond(result, (), DbErrorEventsNotSaved)
      }
    case _ =>
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
