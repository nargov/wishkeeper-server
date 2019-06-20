package co.wishkeeper.server.user.events

import co.wishkeeper.server.Events.{DeviceNotificationIdSet, GeneralSettingPushNotificationEnabledSet, GeneralSettingVibrateEnabledSet, SessionPlatformSet}
import co.wishkeeper.server.User
import org.joda.time.DateTime

object SettingsEventHandlers {
  implicit val deviceNotificationIdSetHandler: UserEventHandler[DeviceNotificationIdSet] =
    (user: User, event: DeviceNotificationIdSet, time: DateTime) =>
      user.copy(settings = user.settings.copy(deviceNotificationId = Option(event.id)))

  implicit val pushNotificationEnabledHandler: UserEventHandler[GeneralSettingPushNotificationEnabledSet] =
    (user: User, event: GeneralSettingPushNotificationEnabledSet, time: DateTime) =>
      user.copy(settings = user.settings.copy(general = user.settings.general.copy(pushNotificationsEnabled = event.enabled)))

  implicit val vibrateEnabledHandler: UserEventHandler[GeneralSettingVibrateEnabledSet] =
    (user: User, event: GeneralSettingVibrateEnabledSet, time: DateTime) =>
      user.copy(settings = user.settings.copy(general = user.settings.general.copy(vibrate = event.enabled)))

  implicit val sessionPlatformSetHandler: UserEventHandler[SessionPlatformSet] =
    (user: User, event: SessionPlatformSet, time: DateTime) =>
      user.copy(settings = user.settings.copy(platform = Option(event.platform)))
}
