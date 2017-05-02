package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.Commands.ConnectFacebookUser
import co.wishkeeper.server.Events.{Event, UserConnected}
import com.wixpress.common.specs2.JMock
import org.joda.time.DateTime
import org.specs2.matcher.MatcherMacros
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import scala.language.experimental.macros

class CommandProcessorTest extends Specification with JMock with MatcherMacros {

  trait Context extends Scope {
    val dataStore = mock[DataStore]
    val commandProcessor: CommandProcessor = new UserCommandProcessor(dataStore)
    val sessionId = UUID.randomUUID()
    val facebookId = "facebook-id"
    val authToken = "auth-token"
  }

  "process a connect command" in new Context {

    checking {
      oneOf(dataStore).lastSequenceNum(having(any[UUID])).willReturn(None)
      oneOf(dataStore).saveUserEvents(having(any[UUID]), having(none), having(any[DateTime]), having(contain(beAnInstanceOf[UserConnected])))
      oneOf(dataStore).saveUserSession(having(any[UUID]), having(equalTo(sessionId)), having(any[DateTime]))
    }

    commandProcessor.process(ConnectFacebookUser(facebookId, authToken, sessionId))
  }

  "return the user id by session id" in new Context {
    val userId = UUID.randomUUID()

    checking {
      oneOf(dataStore).userBySession(sessionId).willReturn(Some(userId))
    }

    commandProcessor.userIdForSession(sessionId) must beSome(userId)
  }

  "notify listeners on new events" in new Context {
    val eventProcessor = mock[EventProcessor]
    val processor = new UserCommandProcessor(dataStore, eventProcessor :: Nil)

    checking {
      ignoring(dataStore)
      atLeast(1).of(eventProcessor).process(having(any[Event]))
    }

    processor.process(ConnectFacebookUser(facebookId, authToken, sessionId))
  }
}
