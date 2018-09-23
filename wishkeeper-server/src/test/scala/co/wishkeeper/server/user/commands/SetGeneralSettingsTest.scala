package co.wishkeeper.server.user.commands

import co.wishkeeper.server.Events.{GeneralSettingPushNotificationEnabledSet, GeneralSettingVibrateEnabledSet, UserEvent}
import co.wishkeeper.server.GeneralSettings
import co.wishkeeper.server.UserTestHelper.aUser
import org.specs2.mutable.Specification

class SetGeneralSettingsTest extends Specification {
  "return push enabled event if changed" in {
    SetGeneralSettings(GeneralSettings(false, false)).process(aUser) must contain[UserEvent](GeneralSettingPushNotificationEnabledSet(false))
  }

  "not return push enabled event if not" in {
    SetGeneralSettings(GeneralSettings(true, false)).process(aUser) must not(contain[UserEvent](GeneralSettingPushNotificationEnabledSet(false)))
  }

  "return vibrate enabled event if changed" in {
    SetGeneralSettings(GeneralSettings(false, false)).process(aUser) must contain[UserEvent](GeneralSettingVibrateEnabledSet(false))
  }

  "not return push enabled event if not" in {
    SetGeneralSettings(GeneralSettings(false, true)).process(aUser) must not(contain[UserEvent](GeneralSettingVibrateEnabledSet(false)))
  }
}
