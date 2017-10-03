package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.FriendRequestStatus.Pending
import org.joda.time.DateTime

sealed trait NotificationData extends Product

case class Notification(id: UUID, data: NotificationData, viewed: Boolean = false, time: DateTime = DateTime.now())

case class FriendRequestNotification(from: UUID,
                                     requestId: UUID,
                                     status: FriendRequestStatus = Pending,
                                     profile: Option[UserProfile] = None) extends NotificationData {

  def withProfile(profile: UserProfile): FriendRequestNotification = this.copy(profile = Option(profile))
}

case class FriendRequestAcceptedNotification(friendId: UUID, requestId: UUID, profile: Option[UserProfile] = None) extends NotificationData {
  def withProfile(profile: UserProfile): FriendRequestAcceptedNotification = this.copy(profile = Option(profile))
}

