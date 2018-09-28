package co.wishkeeper.server.messaging

import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import io.circe.syntax._


sealed trait ServerNotification

object ServerNotification {
  implicit val config = Configuration.default.withDefaults.withDiscriminator("type")

  def toJson(notification: ServerNotification): String = notification.asJson.noSpaces
}

case object NotificationsUpdated extends ServerNotification

case object WishListUpdated extends ServerNotification

case object FriendsListUpdated extends ServerNotification

case object HistoryUpdated extends ServerNotification