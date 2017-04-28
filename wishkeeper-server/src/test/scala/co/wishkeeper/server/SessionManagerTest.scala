package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.Commands.ConnectFacebookUser
import co.wishkeeper.server.Events.UserConnected
import com.wixpress.common.specs2.JMock
import org.joda.time.DateTime
import org.specs2.matcher.MatcherMacros
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import scala.language.experimental.macros

class SessionManagerTest extends Specification with JMock with MatcherMacros {

  trait Context extends Scope {
    val dataStore = mock[DataStore]
    val sessionManager = new UserCommandProcessor(dataStore)
  }

  "SessionManager" should {

    "create a new user session" in new Context {
      val facebookId = "facebook-id"

      checking {
        oneOf(dataStore).lastSequenceNum(having(any[UUID])).willReturn(None)
        oneOf(dataStore).saveUserEvents(having(any[UUID]), having(none), having(any[DateTime]), having(contain(beAnInstanceOf[UserConnected])))
        oneOf(dataStore).updateFacebookIdToUserInfo(having(beEqualTo(facebookId)), having(none),
          having(matchA[UserInfo].facebookData(beSome(matchA[FacebookData].id(facebookId)))))
        oneOf(dataStore).saveUserSession(having(any[UUID]), having(any[UUID]), having(any[DateTime]))
      }

      sessionManager.processConnectFacebookUser(ConnectFacebookUser(facebookId, "auth-token"))
    }

    "return the user id by session id" in new Context {
      val sessionId = UUID.randomUUID()
      val userId = UUID.randomUUID()

      checking {
        oneOf(dataStore).userBySession(sessionId).willReturn(Some(userId))
      }

      sessionManager.userIdForSession(sessionId) must beSome(userId)
    }
  }
}
