package co.wishkeeper.server.user.commands

import java.util.UUID

import co.wishkeeper.server.Events.{SessionPlatformSet, UserEvent, UserFacebookIdSet}
import co.wishkeeper.server.UserTestHelper._
import co.wishkeeper.server.user.Platform
import org.specs2.mutable.Specification

class ConnectFacebookUserTest extends Specification {
  "ConnectFacebookUser" should {
    "Return a set facebook id event only if first time" in {
      val user = aUser
      ConnectFacebookUser("facebook-id", "auth-token", UUID.randomUUID(), None)
        .process(user.withEvent(UserFacebookIdSet(user.id, "fbid"))) must not(contain(anInstanceOf[UserFacebookIdSet]))
    }

    "return a platform set event" in {
      val sessionId = UUID.randomUUID()
      ConnectFacebookUser("facebook-id", "auth-token", sessionId, None, Option(Platform.iOS))
        .process(aUser) must contain[UserEvent](SessionPlatformSet(sessionId, Platform.iOS))
    }

    "return Android platform by default" in {
      val sessionId = UUID.randomUUID()
      ConnectFacebookUser("facebook-id", "auth-token", sessionId, None, None)
        .process(aUser) must contain[UserEvent](SessionPlatformSet(sessionId, Platform.Android))
    }
  }
}
