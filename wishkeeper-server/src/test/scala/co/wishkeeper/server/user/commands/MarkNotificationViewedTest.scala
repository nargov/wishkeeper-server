package co.wishkeeper.server.user.commands

import java.util.UUID.randomUUID

import co.wishkeeper.server.Events.{FriendRequestNotificationCreated, NotificationViewed}
import co.wishkeeper.server.UserTestHelper._
import co.wishkeeper.server.user.{AlreadyViewed, ValidationError}
import org.specs2.mutable.Specification

class MarkNotificationViewedTest extends Specification {
  "MarkNotificationViewed" should {
    "fail validation if notification is already viewed" in {
      val id = randomUUID()
      val notificationCreated = FriendRequestNotificationCreated(id, randomUUID(), randomUUID(), randomUUID())
      val notificationViewed = NotificationViewed(id)
      MarkNotificationViewed.validator.validate(
        aUser.withEvent(notificationCreated).withEvent(notificationViewed),
        MarkNotificationViewed(id)) must beLeft[ValidationError](AlreadyViewed(id))
    }
  }
}
