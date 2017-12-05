package co.wishkeeper.server

import java.util.UUID
import java.util.UUID.randomUUID

import co.wishkeeper.server.Commands.MarkAllNotificationsViewed
import co.wishkeeper.server.Events.{NotificationViewed, UserEvent}
import co.wishkeeper.server.EventsTestHelper.asEventInstant
import co.wishkeeper.server.UserTestHelper._
import org.specs2.mutable.Specification


class MarkAllNotificationsViewedTest extends Specification with NotificationMatchers {
  "should return a NotificationViewed event for notifications" >> {
    val notificationId: UUID = randomUUID()
    val user: User = UserTestHelper.aUser.withFriendRequestNotification(notificationId, randomUUID(), randomUUID())
    val events: List[UserEvent] = MarkAllNotificationsViewed.process(user)
    events must contain(aNotificationViewedEvent(notificationId))
  }

  "should not return an event for a notification that was already viewed" >> {
    val notificationId: UUID = randomUUID()
    val user: User = UserTestHelper.aUser.withFriendRequestNotification(notificationId, randomUUID(), randomUUID()).applyEvent(asEventInstant(
      NotificationViewed(notificationId)
    ))
    val events: List[UserEvent] = MarkAllNotificationsViewed.process(user)
    events must beEmpty
  }

}
