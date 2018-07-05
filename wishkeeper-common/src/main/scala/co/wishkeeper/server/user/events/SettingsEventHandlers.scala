package co.wishkeeper.server.user.events

import co.wishkeeper.server.Events.DeviceNotificationIdSet
import co.wishkeeper.server.User
import org.joda.time.DateTime

object SettingsEventHandlers {
  implicit val deviceNotificationIdSetHandler = new UserEventHandler[DeviceNotificationIdSet] {
    override def apply(user: User, event: DeviceNotificationIdSet, time: DateTime): User =
      user.copy(settings = user.settings.copy(deviceNotificationId = Option(event.id)))
  }
}
