package co.wishkeeper.server.notifications

import java.util.UUID.randomUUID

import co.wishkeeper.server.NotificationsData.FriendRequestNotification
import co.wishkeeper.server.UserProfile
import org.specs2.mutable.Specification

class FriendRequestNotificationTest extends Specification {
  "should return an instance with the given profile" in {
    val profile = UserProfile(name = Option("name"))
    FriendRequestNotification(randomUUID(), randomUUID()).withProfile(profile).profile must beSome(profile)
  }
}
