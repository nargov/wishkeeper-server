package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.FriendRequestStatus.Pending
import co.wishkeeper.server.NotificationsData.{NotificationData, PeriodicWakeup}
import io.circe.generic.extras.Configuration
import org.joda.time.DateTime


object NotificationsData {

  sealed trait NotificationData

  sealed trait WishNotification extends NotificationData {
    val wishId: UUID
  }

  object PeriodicWakeup extends NotificationData

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


