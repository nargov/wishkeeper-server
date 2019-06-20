package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.Events.{SessionPlatformSet, UserEvent}
import co.wishkeeper.server.UserTestHelper.aUser
import co.wishkeeper.server.user.Platform
import org.specs2.mutable.Specification

class ConnectGoogleUserTest extends Specification {
  "ConnectGoogleUser" should {
    "return a session platform set event" in {
      val sessionId = UUID.randomUUID()
      ConnectGoogleUser("access-token", "id-token", sessionId, Option(Platform.iOS))
        .process(aUser) must contain[UserEvent](SessionPlatformSet(sessionId, Platform.iOS))
    }

    "return a default platform set event" in {
      val sessionId = UUID.randomUUID()
      ConnectGoogleUser("access-token", "id-token", sessionId, None)
        .process(aUser) must contain[UserEvent](SessionPlatformSet(sessionId, Platform.Android))
    }
  }
}
