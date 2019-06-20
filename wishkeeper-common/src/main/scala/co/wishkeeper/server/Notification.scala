package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.FriendRequestStatus.Pending
import co.wishkeeper.server.NotificationsData.{NotificationData, PeriodicWakeup}
import io.circe.generic.extras.Configuration
import org.joda.time.DateTime


object NotificationsData {

  sealed trait NotificationData

  sealed trait UserNotification extends NotificationData {
    def title: String

    def content: String
  }

  sealed trait WishNotification extends UserNotification {
    val wishId: UUID
  }

  object PeriodicWakeup extends NotificationData

  case class FriendRequestNotification(from: UUID,
                                       requestId: UUID,
                                       status: FriendRequestStatus = Pending,
                                       profile: Option[UserProfile] = None) extends UserNotification {

    override def title: String = "New friend request"

    override def content: String = s"\u202A${profile.map(_.name).getOrElse("Someone")} sent you a friend request."

    def withProfile(profile: UserProfile): FriendRequestNotification = this.copy(profile = Option(profile))
  }

  case class FriendRequestAcceptedNotification(friendId: UUID, requestId: UUID, profile: Option[UserProfile] = None) extends UserNotification {

    override def title: String = "Friend request accepted"

    override def content: String = s"\u202A${profile.map(_.name).getOrElse("Someone")} accepted your friend request."

    def withProfile(profile: UserProfile): FriendRequestAcceptedNotification = this.copy(profile = Option(profile))
  }

  case class WishReservedNotification(wishId: UUID,
                                      reserver: UUID,
                                      reserverProfile: Option[UserProfile] = None,
                                      wishName: Option[String] = None) extends WishNotification {

    override val content: String = s"Someone reserved the wish $wishName from your wishlist."

    override val title: String = "Someone reserved one of your wishes"
  }

  case class WishUnreservedNotification(wishId: UUID,
                                        reserver: UUID,
                                        reserverProfile: Option[UserProfile] = None,
                                        wishName: Option[String] = None) extends WishNotification {
    override def title: String = "Your wish is no longer reserved"

    override def content: String = s"Someone changed their mind about getting you $wishName."
  }

  case object EmailVerifiedNotification extends NotificationData

}

case class Notification(id: UUID, data: NotificationData, viewed: Boolean = false, time: DateTime = DateTime.now())

case class UserNotifications(list: List[Notification], unread: Int)

trait TypedJson[T] {
  def toJson(t: T): String
}

object TypedJson {

  import io.circe.generic.extras.auto._
  import io.circe.syntax._

  implicit val circeConfig = Configuration.default.withDefaults.withDiscriminator("type")

  implicit val pushNotificationJson = new TypedJson[PushNotification] {
    override def toJson(n: PushNotification): String = n.asJson.noSpaces
  }

  implicit val broadcastNotificationJson = new TypedJson[BroadcastNotification] {
    override def toJson(n: BroadcastNotification): String = n.asJson.noSpaces
  }
}

case class PushNotification(userId: UUID, notificationId: UUID, data: NotificationData)

case class BroadcastNotification(notificationId: Option[UUID], data: NotificationData)

object BroadcastNotifications {
  val periodicWakeup = BroadcastNotification(None, PeriodicWakeup)
}


