package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.FriendRequestNotificationStatus.Pending
import io.circe.Encoder
import org.joda.time.DateTime

sealed trait NotificationData

case class Notification(id: UUID, data: NotificationData, viewed: Boolean = false, time: DateTime = DateTime.now())

case class FriendRequestNotification(from: UUID,
                                     status: FriendRequestNotificationStatus = Pending,
                                     profile: Option[UserProfile] = None) extends NotificationData {

  def withProfile(profile: UserProfile): FriendRequestNotification = this.copy(profile = Option(profile))
}

sealed trait FriendRequestNotificationStatus
object FriendRequestNotificationStatus {
  case object Pending extends FriendRequestNotificationStatus
  case object Approved extends FriendRequestNotificationStatus
  case object Ignored extends FriendRequestNotificationStatus

  implicit val statusEncoder: Encoder[FriendRequestNotificationStatus] = Encoder.encodeString.contramap(_.toString)
}