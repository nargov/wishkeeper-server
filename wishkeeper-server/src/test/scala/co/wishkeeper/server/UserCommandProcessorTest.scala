package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.Events._
import co.wishkeeper.server.EventsTestHelper.{asEventInstants, userConnectEvent}
import co.wishkeeper.server.user.DummyError
import co.wishkeeper.server.user.commands.{ConnectFacebookUser, SetFacebookUserInfo, UserCommand, UserCommandValidator}
import com.wixpress.common.specs2.JMock
import org.joda.time.DateTime
import org.specs2.matcher.{Matcher, MatcherMacros}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import scala.language.experimental.macros

class UserCommandProcessorTest extends Specification with JMock with MatcherMacros {

  "notify event processors on new events" in new EventProcessorContext {
    assumeNoSuchUserForFacebookId()
    ignoringSaveUserSession()
    ignoringSaveUserEvents()

    checking {
      atLeast(1).of(eventProcessor).process(having(any[Event]), having(any[UUID])).willReturn(Nil)
    }

    processUserConnect()
  }

  "create new user on ConnectFacebookUser if user doesn't exist" in new EventProcessorContext {
    assumeNoSuchUserForFacebookId()
    ignoringSaveUserEvents()
    ignoringProcessFacebookIdSet()
    ignoringSaveUserSession()

    checking {
      oneOf(eventProcessor).process(having(any[UserConnected]), having(any[UUID])).willReturn(Nil)
    }

    processUserConnect()
  }

  "load user if exists on ConnectFacebookUser" in new EventProcessorContext {
    assumeExistingUser()
    assumeHasSequenceNum()
    ignoringSaveUserEvents()
    ignoringSaveUserSession()
    ignoringProcessFacebookIdSet()

    checking {
      oneOf(eventProcessor).process(having(aUserConnectedEventFor(userId)), having(===(userId))).willReturn(Nil)
    }

    processUserConnect()
  }

  "load user if session exists" in new Context {
    assumeHasSequenceNum()
    ignoringSaveUserEvents()

    checking {
      oneOf(dataStore).userBySession(sessionId).willReturn(Some(userId))
      oneOf(dataStore).userEvents(userId).willReturn(asEventInstants(List(userConnectEvent(userId))))
    }

    commandProcessor.process(SetFacebookUserInfo(), Some(sessionId))
  }

  "save user session on connect" in new Context {
    assumeExistingUser()
    assumeHasSequenceNum()
    ignoringSaveUserEvents()

    checking {
      oneOf(dataStore).saveUserSession(having(===(userId)), having(===(sessionId)), having(any[DateTime]))
    }

    processUserConnect()
  }

  "retry saving user events on concurrent modification error" in new Context {
    assumeExistingUser()
    assumeHasSequenceNum()

    checking {
      exactly(2).of(dataStore).saveUserEvents(
        having(===(userId)),
        having(beSome(3L)),
        having(any[DateTime]),
        having(contain(any[UserEvent]))
      ).will(returnValue(false), returnValue(true))
    }

    commandProcessor.process(SetFacebookUserInfo(name = Option("name")), Some(sessionId))
  }

  "notify event processors only once" in new EventProcessorContext {
    assumeExistingUser()
    assumeHasSequenceNum()

    checking {
      allowing(dataStore).saveUserEvents(having(===(userId)), having(beSome(3L)), having(any[DateTime]), having(contain(any[UserEvent]))).
        will(returnValue(false), returnValue(true))
      oneOf(eventProcessor).process(having(any[Event]), having(===(userId))).willReturn(Nil)
    }

    commandProcessor.process(SetFacebookUserInfo(name = Option("name")), Some(sessionId))
  }

  "return failure when validation fails" in new Context {
    val validationError = Left(DummyError)
    val validator: UserCommandValidator[DummyCommand] = mock[UserCommandValidator[DummyCommand]]

    assumeExistingUser()
    assumeHasSequenceNum()

    checking {
      oneOf(validator).validate(having(any[User]), having(any[UserCommand])).willReturn(validationError)
    }

    commandProcessor.validatedProcess(DummyCommand(), userId)(validator) must beLeft
  }

  "publish events that come from event processors" in new EventProcessorContext {
    assumeExistingUser()
    assumeHasSequenceNum()
    ignoringSaveUserEvents()

    val alwaysValid: UserCommandValidator[DummyCommand] = (_: User, _: DummyCommand) => Right(())
    val expectedEvent = UserFirstNameSet(userId, "Roger")

    checking {
      allowing(eventProcessor).process(NoOp, userId).willReturn((userId, expectedEvent) :: Nil)
      oneOf(eventProcessor).process(expectedEvent, userId).willReturn(Nil)
    }

    commandProcessor.validatedProcess(DummyCommand(NoOp :: Nil), userId)(alwaysValid)
  }


  trait Context extends Scope {
    val userId: UUID = UUID.randomUUID()
    val dataStore: DataStore = mock[DataStore]
    val commandProcessor: CommandProcessor = new UserCommandProcessor(dataStore)
    val sessionId: UUID = UUID.randomUUID()
    val facebookId = "facebook-id"
    val authToken = "auth-token"

    def assumeExistingUser() = checking {
      allowing(dataStore).userEvents(userId).willReturn(
        UserEventInstant(UserConnected(userId, DateTime.now().minusDays(1), UUID.randomUUID()), DateTime.now().minusDays(1)) :: Nil)
      allowing(dataStore).userBySession(sessionId).willReturn(Some(userId))
      allowing(dataStore).userIdByFacebookId(facebookId).willReturn(Some(userId))
    }

    def assumeHasSequenceNum() = checking {
      allowing(dataStore).lastSequenceNum(userId).willReturn(Some(3L))
    }

    def assumeNoSuchUserForFacebookId() = checking {
      allowing(dataStore).userIdByFacebookId(facebookId).willReturn(None)
    }

    def processUserConnect() = {
      commandProcessor.process(ConnectFacebookUser(facebookId, authToken, sessionId))
    }

    def ignoringSaveUserEvents() = checking {
      ignoring(dataStore).saveUserEvents(having(any[UUID]), having(any), having(any[DateTime]), having(any[Seq[Event]])).willReturn(true)
    }

    def ignoringSaveUserSession() = checking {
      ignoring(dataStore).saveUserSession(having(any[UUID]), having(any[UUID]), having(any[DateTime]))
    }
  }

  trait EventProcessorContext extends Context {
    val eventProcessor: EventProcessor = mock[EventProcessor]
    override val commandProcessor = new UserCommandProcessor(dataStore, eventProcessor :: Nil)

    def ignoringProcessFacebookIdSet() = checking {
      ignoring(eventProcessor).process(having(any[UserFacebookIdSet]), having(any[UUID])).willReturn(Nil)
    }
  }


  def aUserConnectedEventFor(userId: UUID): Matcher[UserConnected] = ===(userId) ^^ ((_: UserConnected).userId)
}

case class DummyCommand(eventsToReturn: List[UserEvent] = Nil) extends UserCommand {
  override def process(user: User): List[UserEvent] = eventsToReturn
}