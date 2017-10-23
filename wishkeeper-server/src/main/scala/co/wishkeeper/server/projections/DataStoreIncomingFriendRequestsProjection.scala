package co.wishkeeper.server.projections

import java.util.UUID
import java.util.UUID.randomUUID

import co.wishkeeper.server.Events._
import co.wishkeeper.server.FriendRequestStatus.Approved
import co.wishkeeper.server.NotificationsData.{FriendRequestAcceptedNotification, FriendRequestNotification}
import co.wishkeeper.server._
import org.joda.time.DateTime

trait IncomingFriendRequestsProjection

class DataStoreIncomingFriendRequestsProjection(dataStore: DataStore) extends IncomingFriendRequestsProjection with EventProcessor {
  override def process(event: Event): Unit = event match {
    case FriendRequestSent(sender, userId, id) =>
      id.foreach { _ =>
        val lastSequenceNum = dataStore.lastSequenceNum(userId)
        dataStore.saveUserEvents(userId, lastSequenceNum, DateTime.now(), List(
          FriendRequestReceived(userId, sender, id)
        ))
      }
    case _ =>
  }
}

trait NotificationsProjection extends EventProcessor {
  def notificationsFor(userId: UUID): List[Notification]
}

class DataStoreNotificationsProjection(dataStore: DataStore) extends NotificationsProjection {
  override def process(event: Event): Unit = event match {
    case FriendRequestSent(sender, userId, id) => id.foreach { requestId =>
      val lastSeqNum = dataStore.lastSequenceNum(userId)
      dataStore.saveUserEvents(userId, lastSeqNum, DateTime.now(), List(
        FriendRequestNotificationCreated(randomUUID(), userId, sender, requestId)
      ))
    }
    case FriendRequestStatusChanged(userId, reqId, from, status) if status == Approved =>
      val lastSeqNum = dataStore.lastSequenceNum(from)
      dataStore.saveUserEvents(from, lastSeqNum, DateTime.now(), List(
        FriendRequestAcceptedNotificationCreated(randomUUID(), from, userId, reqId)
      ))
    case _ =>
  }

  def notificationsFor(userId: UUID): List[Notification] = {
    val notifications = User.replay(dataStore.userEvents(userId)).notifications
    notifications.map(notification => notification.copy(data = notification.data match {
      case friendReq: FriendRequestNotification =>
        val sender = User.replay(dataStore.userEvents(friendReq.from))
        friendReq.withProfile(sender.userProfile)
      case accepted: FriendRequestAcceptedNotification =>
        val friend = User.replay(dataStore.userEvents(accepted.friendId))
        accepted.withProfile(friend.userProfile)
      case x => x
    }))
  }
}

trait FriendRequestsProjection

class DataStoreFriendRequestsProjection(dataStore: DataStore) extends FriendRequestsProjection with EventProcessor {
  override def process(event: Event): Unit = {
    event match {
      case FriendRequestStatusChanged(_, requestId, from, status) =>
        dataStore.saveUserEvents(from, dataStore.lastSequenceNum(from), DateTime.now(), List(
          FriendRequestStatusChanged(from, requestId, from, status)
        ))
      case _ =>
    }
  }
}