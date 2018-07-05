package co.wishkeeper.server.user.commands

import co.wishkeeper.server.Events.DeviceNotificationIdSet
import co.wishkeeper.server.EventsTestHelper
import co.wishkeeper.server.UserTestHelper._
import co.wishkeeper.server.user.{NoChange, ValidationError}
import org.specs2.mutable.Specification

class SetDeviceNotificationIdTest extends Specification {
  "Fail validation if device notification id hasn't changed" in {
    val id = "id"
    val user = aUser.applyEvent(EventsTestHelper.asEventInstant(DeviceNotificationIdSet(id)))
    SetDeviceNotificationId.validator.validate(user, SetDeviceNotificationId(id)) must beLeft[ValidationError](NoChange)
  }
}
