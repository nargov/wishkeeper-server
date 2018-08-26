package co.wishkeeper.server.api

import java.util.UUID
import java.util.UUID.randomUUID

import co.wishkeeper.server.search.UserSearchProjection
import co.wishkeeper.server.{CommandProcessor, DataStore}
import co.wishkeeper.server.user.commands.{DeleteUserPicture, SetFlagFacebookFriendsListSeen}
import com.wixpress.common.specs2.JMock
import org.specs2.mutable.Specification

class DelegatingManagementApiTest extends Specification with JMock {

  "should apply the reset flag facebook friends seen event" in {
    val userId: UUID = randomUUID()
    val commandProcessor: CommandProcessor = mock[CommandProcessor]

    checking {
      oneOf(commandProcessor).process(SetFlagFacebookFriendsListSeen(false), userId)
    }

    new DelegatingManagementApi(null, null, null, commandProcessor, null, null)
      .resetFacebookFriendsSeenFlag(userId)
  }

  "should delete user picture" in {
    val userId: UUID = randomUUID()
    val commandProcessor: CommandProcessor = mock[CommandProcessor]

    checking {
      oneOf(commandProcessor).validatedProcess(DeleteUserPicture, userId).willReturn(Right(()))
    }

    new DelegatingManagementApi(null, null, null, commandProcessor, null, null)
      .deleteUserPicture(userId)
  }
}
