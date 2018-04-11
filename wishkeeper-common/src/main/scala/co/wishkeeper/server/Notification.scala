package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.FriendRequestStatus.Pending
import co.wishkeeper.server.NotificationsData.NotificationData
import org.joda.time.DateTime


object NotificationsData {

  sealed trait NotificationData
  sealed trait WishNotification extends NotificationData {
    val wishId: UUID
  }

  case class FriendRequestNotification(from: UUID,
                                       requestId: UUID,
                                       status: FriendRequestStatus = Pending,
                                       profile: Option[UserProfile] = None) extends NotificationData {

    def withProfile(profile: UserProfile): FriendRequestNotification = this.copy(profile = Option(profile))
  }

  case class FriendRequestAcceptedNotification(friendId: UUID, requestId: UUID, profile: Option[UserProfile] = None) extends NotificationData {
    def withProfile(profile: UserProfile): FriendRequestAcceptedNotification = this.copy(profile = Option(profile))
  }

  case class WishReservedNotification(wishId: UUID,
                                      reserver: UUID,
                                      reserverProfile: Option[UserProfile] = None,
                                      wishName: Option[String] = None) extends WishNotification

  case class WishUnreservedNotification(wishId: UUID,
                                        reserver: UUID,
                                        reserverProfile: Option[UserProfile] = None,
                                        wishName: Option[String] = None) extends WishNotification
}

case class Notification(id: UUID, data: NotificationData, viewed: Boolean = false, time: DateTime = DateTime.now())

case class UserNotifications(list: List[Notification], unread: Int)