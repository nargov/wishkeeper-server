package co.wishkeeper.server.projections

import java.util.UUID

import co.wishkeeper.server.Events.{Event, FriendRequestNotificationCreated, FriendRequestReceived, FriendRequestSent}
import co.wishkeeper.server._
import org.joda.time.DateTime

trait IncomingFriendRequestsProjection

class DataStoreIncomingFriendRequestsProjection(dataStore: DataStore) extends IncomingFriendRequestsProjection with EventProcessor {
  override def process(event: Event): Unit = event match {
    case FriendRequestSent(sender, userId) =>
      val lastSequenceNum = dataStore.lastSequenceNum(userId)
      dataStore.saveUserEvents(userId, lastSequenceNum, DateTime.now(), List(
        FriendRequestReceived(userId, sender)
      ))
    case _ =>
  }
}

trait NotificationsProjection extends EventProcessor {
  def notificationsFor(userId: UUID): List[Notification]
}

class DataStoreNotificationsProjection(dataStore: DataStore) extends NotificationsProjection {
  override def process(event: Event): Unit = event match {
    case FriendRequestSent(sender, userId) =>
      val lastSeqNum = dataStore.lastSequenceNum(userId)
      dataStore.saveUserEvents(userId, lastSeqNum, DateTime.now(), List(
        FriendRequestNotificationCreated(UUID.randomUUID(), userId, sender)
      ))
    case _ =>
  }

  def notificationsFor(userId: UUID): List[Notification] = {
    val notifications = User.replay(dataStore.userEvents(userId)).notifications
    notifications.map(notification => notification.copy(data = notification.data match {
      case friendReq: FriendRequestNotification =>
        val sender = User.replay(dataStore.userEvents(friendReq.from))
        friendReq.withProfile(sender.userProfile)
      case x => x
    }))
  }
}
