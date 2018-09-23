package co.wishkeeper.server.user.events

import co.wishkeeper.server.Events.{DeviceNotificationIdSet, GeneralSettingPushNotificationEnabledSet, GeneralSettingVibrateEnabledSet}
import co.wishkeeper.server.User
import org.joda.time.DateTime

object SettingsEventHandlers {
  implicit val deviceNotificationIdSetHandler = new UserEventHandler[DeviceNotificationIdSet] {
    override def apply(user: User, event: DeviceNotificationIdSet, time: DateTime): User =
      user.copy(settings = user.settings.copy(deviceNotificationId = Option(event.id)))
  }

  implicit val pushNotificationEnabledHandler = new UserEventHandler[GeneralSettingPushNotificationEnabledSet] {
    override def apply(user: User, event: GeneralSettingPushNotificationEnabledSet, time: DateTime): User =
      user.copy(settings = user.settings.copy(general = user.settings.general.copy(pushNotificationsEnabled = event.enabled)))
  }

  implicit val vibrateEnabledHandler = new UserEventHandler[GeneralSettingVibrateEnabledSet] {
    override def apply(user: User, event: GeneralSettingVibrateEnabledSet, time: DateTime): User =
      user.copy(settings = user.settings.copy(general = user.settings.general.copy(vibrate = event.enabled)))
  }
}
