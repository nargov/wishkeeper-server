package co.wishkeeper.server.notifications

import java.util.UUID

import co.wishkeeper.server.{FriendRequestNotification, UserProfile}
import org.specs2.mutable.Specification

class FriendRequestNotificationTest extends Specification {
  "should return an instance with the given profile" in {
    val profile = UserProfile(name = Option("name"))
    FriendRequestNotification(UUID.randomUUID()).withProfile(profile).profile must beSome(profile)
  }
}
