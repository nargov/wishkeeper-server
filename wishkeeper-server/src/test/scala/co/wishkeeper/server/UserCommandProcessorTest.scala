package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.Commands.{ConnectFacebookUser, SetFacebookUserInfo}
import co.wishkeeper.server.Events.{Event, UserConnected, UserFacebookIdSet}
import com.wixpress.common.specs2.JMock
import org.joda.time.DateTime
import org.specs2.matcher.{Matcher, MatcherMacros}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import scala.language.experimental.macros

class UserCommandProcessorTest extends Specification with JMock with MatcherMacros {

  trait Context extends Scope {
    val userId = UUID.randomUUID()
    val dataStore = mock[DataStore]
    val commandProcessor: CommandProcessor = new UserCommandProcessor(dataStore)
    val sessionId = UUID.randomUUID()
    val facebookId = "facebook-id"
    val authToken = "auth-token"
  }

  trait EventProcessorContext extends Context {
    val eventProcessor = mock[EventProcessor]
    override val commandProcessor = new UserCommandProcessor(dataStore, eventProcessor :: Nil)
  }

  "return the user id by session id" in new Context {
    checking {
      oneOf(dataStore).userBySession(sessionId).willReturn(Some(userId))
    }

    commandProcessor.userIdForSession(sessionId) must beSome(userId)
  }

  "notify event processors on new events" in new EventProcessorContext {
    checking {
      allowing(dataStore).userIdByFacebookId(facebookId).willReturn(None)
      ignoring(dataStore).saveUserSession(having(any[UUID]), having(any[UUID]), having(any[DateTime]))
      ignoring(dataStore).saveUserEvents(having(any[UUID]), having(beNone), having(any[DateTime]), having(any[Seq[Event]]))
      atLeast(1).of(eventProcessor).process(having(any[Event]))
    }

    commandProcessor.process(ConnectFacebookUser(facebookId, authToken, sessionId))
  }

  "create new user on ConnectFacebookUser if user doesn't exist" in new EventProcessorContext {
    checking {
      allowing(dataStore).userIdByFacebookId(facebookId).willReturn(None)
      oneOf(eventProcessor).process(having(any[UserConnected]))
      ignoring(eventProcessor).process(having(any[UserFacebookIdSet]))
      never(dataStore).lastSequenceNum(having(any[UUID]))
      never(dataStore).userEventsFor(having(any[UUID]))
      ignoring(dataStore).saveUserEvents(having(any[UUID]), having(beNone), having(any[DateTime]), having(any[Seq[Event]]))
      ignoring(dataStore).saveUserSession(having(any[UUID]), having(any[UUID]), having(any[DateTime]))
    }

    commandProcessor.process(ConnectFacebookUser(facebookId, authToken, sessionId))
  }

  "load user if exists on ConnectFacebookUser" in new EventProcessorContext {
    checking {
      allowing(dataStore).userIdByFacebookId(facebookId).willReturn(Some(userId))
      oneOf(eventProcessor).process(having(aUserConnectedEventFor(userId)))
      ignoring(eventProcessor).process(having(any[UserFacebookIdSet]))
      allowing(dataStore).lastSequenceNum(userId).willReturn(Some(3L))
      allowing(dataStore).userEventsFor(userId).willReturn(UserConnected(userId, DateTime.now().minusDays(1), UUID.randomUUID()) :: Nil)
      ignoring(dataStore).saveUserEvents(having(any[UUID]), having(beSome(any)), having(any[DateTime]), having(any[Seq[Event]]))
      ignoring(dataStore).saveUserSession(having(any[UUID]), having(any[UUID]), having(any[DateTime]))
    }

    commandProcessor.process(ConnectFacebookUser(facebookId, authToken, sessionId))
  }

  "load user if session exists" in new Context {
    checking {
      oneOf(dataStore).userBySession(sessionId).willReturn(Some(userId))
      oneOf(dataStore).userEventsFor(userId).willReturn(UserConnected(userId, DateTime.now().minusDays(1), UUID.randomUUID()) :: Nil)
      allowing(dataStore).lastSequenceNum(userId).willReturn(Some(3L))
      ignoring(dataStore).saveUserEvents(having(any[UUID]), having(beSome(any)), having(any[DateTime]), having(any[Seq[Event]]))
    }

    commandProcessor.process(SetFacebookUserInfo(), Some(sessionId))
  }

  def aUserConnectedEventFor(userId: UUID): Matcher[UserConnected] = ===(userId) ^^ {(_:UserConnected).userId}
}
