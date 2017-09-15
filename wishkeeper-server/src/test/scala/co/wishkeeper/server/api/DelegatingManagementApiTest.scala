package co.wishkeeper.server.api

import java.util.UUID

import co.wishkeeper.server.CommandProcessor
import co.wishkeeper.server.Commands.SetFlagFacebookFriendsListSeen
import com.wixpress.common.specs2.JMock
import org.specs2.mutable.Specification

class DelegatingManagementApiTest extends Specification with JMock {
  "Applies the reset flag facebook friends seen event" in {
    val commandProcessor = mock[CommandProcessor]
    val userId = UUID.randomUUID()

    checking {
      oneOf(commandProcessor).process(SetFlagFacebookFriendsListSeen(false), userId)
    }

    new DelegatingManagementApi(null, null, null, commandProcessor).resetFacebookFriendsSeenFlag(userId)
  }
}
