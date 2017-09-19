package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.FriendRequestNotificationStatus.Pending

sealed trait NotificationData

case class Notification(notificationData: NotificationData, viewed: Boolean = false)

case class FriendRequestNotification(from: UUID, status: FriendRequestNotificationStatus = Pending) extends NotificationData

sealed trait FriendRequestNotificationStatus
object FriendRequestNotificationStatus {
  case object Pending extends FriendRequestNotificationStatus
  case object Approved extends FriendRequestNotificationStatus
  case object Ignored extends FriendRequestNotificationStatus
}
