package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.Events._
import co.wishkeeper.server.EventsTestHelper.{EventsList, asEventInstants, userConnectEvent}
import co.wishkeeper.server.user.{DummyError, Gender}
import co.wishkeeper.server.user.commands._
import com.wixpress.common.specs2.JMock
import org.jmock.lib.concurrent.DeterministicExecutor
import org.joda.time.{DateTime, LocalDate}
import org.specs2.matcher.{Matcher, MatcherMacros}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import scala.concurrent.ExecutionContext
import scala.language.experimental.macros

class UserCommandProcessorTest extends Specification with JMock with MatcherMacros {

  "notify event processors on new events" in new EventProcessorContext {
    assumeNoSuchUserForFacebookId()
    ignoringSaveUserSession()
    ignoringSaveUserEvents()

    checking {
      atLeast(1).of(eventProcessor).process(having(any[Event]), having(any[UUID])).willReturn(Nil)
    }

    processConnectWithFacebook()
  }

  "create new user on ConnectFacebookUser if user doesn't exist" in new EventProcessorContext {
    assumeNoSuchUserForFacebookId()
    ignoringSaveUserEvents()
    ignoringProcessFacebookIdSet()
    ignoringSaveUserSession()

    checking {
      oneOf(eventProcessor).process(having(any[UserConnected]), having(any[UUID])).willReturn(Nil)
    }

    processConnectWithFacebook()
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

    processConnectWithFacebook()
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

    processConnectWithFacebook()
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

  "validate google id token when connecting with google" in new ConnectContext {
    assumeNewUser()
    ignoringSaveUserEvents()
    ignoringSaveUserSession()
    checking {
      ignoring(dataStore).saveUserByEmail(having(any), having(any))
      allowing(dataStore).userIdByEmail(email).willReturn(None)
      oneOf(googleAuth).validateIdToken(idToken).willReturn(Right(googleUser))
    }

    commandProcessor.connectWithGoogle(ConnectGoogleUser(accessToken, idToken, sessionId))
  }

  "return error if google id fails validation" in new ConnectContext {
    checking {
      allowing(googleAuth).validateIdToken(idToken).willReturn(Left(GoogleAuthError("error message")))
    }

    commandProcessor.connectWithGoogle(ConnectGoogleUser(accessToken, idToken, sessionId)) must beLeft(anInstanceOf[GoogleAuthError])
  }

  "save user session when connecting with google" in new ConnectContext {
    assumeNewUser()
    ignoringSaveUserEvents()
    checking {
      ignoring(dataStore).saveUserByEmail(having(any), having(any))
      allowing(dataStore).userIdByEmail(email).willReturn(None)
      allowing(googleAuth).validateIdToken(idToken).willReturn(Right(googleUser))
      oneOf(dataStore).saveUserSession(having(any[UUID]), having(===(sessionId)), having(any[DateTime])).willReturn(true)
    }

    commandProcessor.connectWithGoogle(ConnectGoogleUser(accessToken, idToken, sessionId))
  }

  "save user connection event when connecting with google" in new ConnectContext {
    assumeNewUser()
    ignoringSaveUserSession()
    checking {
      ignoring(dataStore).saveUserByEmail(having(any), having(any))
      allowing(dataStore).userIdByEmail(email).willReturn(None)
      allowing(googleAuth).validateIdToken(idToken).willReturn(Right(googleUser))
      oneOf(dataStore).saveUserEvents(having(any[UUID]), having(beNone), having(any[DateTime]), having(contain(anInstanceOf[UserConnected])))
        .willReturn(true)
    }

    commandProcessor.connectWithGoogle(ConnectGoogleUser(accessToken, idToken, sessionId))
  }

  "connect existing user if found by email" in new ConnectContext {
    assumeExistingUser()
    checking {
      allowing(googleAuth).validateIdToken(idToken).willReturn(Right(googleUser))
      allowing(dataStore).userIdByEmail(email).willReturn(Option(userId))
      oneOf(dataStore).saveUserSession(having(===(userId)), having(===(sessionId)), having(any[DateTime])).willReturn(true)
      oneOf(dataStore).saveUserEvents(having(===(userId)), having(some[Long]), having(any[DateTime]), having(contain(anInstanceOf[UserConnected])))
        .willReturn(true)
    }

    commandProcessor.connectWithGoogle(ConnectGoogleUser(accessToken, idToken, sessionId))
  }

  "save google user events for user data" in new ConnectContext {
    assumeExistingUser()
    checking {
      allowing(googleAuth).validateIdToken(idToken).willReturn(Right(googleUser))
      allowing(dataStore).userIdByEmail(email).willReturn(Option(userId))
      oneOf(dataStore).saveUserSession(having(===(userId)), having(===(sessionId)), having(any[DateTime])).willReturn(true)
      oneOf(dataStore).saveUserEvents(having(===(userId)), having(some[Long]), having(any[DateTime]), having(contain(allOf(
        UserNameSet(userId, name),
        UserFirstNameSet(userId, givenName),
        UserLastNameSet(userId, familyName),
        UserEmailSet(userId, email),
        UserPictureSet(userId, photo),
        UserLocaleSet(userId, locale)
      )))).willReturn(true)
    }

    commandProcessor.connectWithGoogle(ConnectGoogleUser(accessToken, idToken, sessionId))
  }

  "not save google user data if already set" in new ConnectContext {
    assumeHasSequenceNum()

    checking {
      allowing(googleAuth).validateIdToken(idToken).willReturn(Right(googleUser))
      allowing(dataStore).userIdByEmail(email).willReturn(Option(userId))
      oneOf(dataStore).saveUserSession(having(===(userId)), having(===(sessionId)), having(any[DateTime])).willReturn(true)
      oneOf(dataStore).userEvents(userId).willReturn(EventsList(userId)
        .withName(name)
        .withFirstName(givenName)
        .withLastName(familyName)
        .withPic(photo)
        .withEvent(UserLocaleSet(userId, locale))
        .withEmail(email)
        .list)
      oneOf(dataStore).saveUserEvents(having(===(userId)), having(some[Long]), having(any[DateTime]), having(not(contain(allOf(
        UserNameSet(userId, name),
        UserFirstNameSet(userId, givenName),
        UserLastNameSet(userId, familyName),
        UserEmailSet(userId, email),
        UserPictureSet(userId, photo),
        UserLocaleSet(userId, locale)
      ))))).willReturn(true)
    }

    commandProcessor.connectWithGoogle(ConnectGoogleUser(accessToken, idToken, sessionId))
  }

  "save user by email when connecting with google" in new ConnectContext {
    assumeNewUser()
    ignoringSaveUserEvents()
    ignoringSaveUserSession()
    checking {
      allowing(googleAuth).validateIdToken(idToken).willReturn(Right(googleUser))
      allowing(dataStore).userIdByEmail(email).willReturn(None)
      oneOf(dataStore).saveUserByEmail(having(===(email)), having(any[UUID]))
    }

    commandProcessor.connectWithGoogle(ConnectGoogleUser(accessToken, idToken, sessionId))
  }

  "save events for gender and birthday from google user data" in new ConnectContext {
    assumeExistingUser()

    checking {
      allowing(googleAuth).validateIdToken(idToken).willReturn(Right(googleUser))
      allowing(dataStore).userIdByEmail(email).willReturn(Option(userId))
      allowing(dataStore).saveUserSession(having(===(userId)), having(===(sessionId)), having(any[DateTime])).willReturn(true)
      oneOf(dataStore).saveUserEvents(having(===(userId)), having(some[Long]), having(any[DateTime]), having(contain(allOf(
        UserBirthdaySet(userId, birthday.toString("MM/dd/yyyy")),
        UserGenderSet2(Gender.Female, None, None)
      )))).willReturn(true)
    }

    commandProcessor.connectWithGoogle(ConnectGoogleUser(accessToken, idToken, sessionId))
  }

  "connect with existing firebase user" in new ConnectContext {
    assumeExistingUser()

    checking {
      allowing(dataStore).userIdByEmail(email).willReturn(Option(userId))
      oneOf(firebaseAuth).validate(authToken).willReturn(Right(EmailAuthData(email, verified = true)))
      oneOf(dataStore).saveUserSession(having(===(userId)), having(===(sessionId)), having(any[DateTime]))
      oneOf(dataStore).saveUserEvents(having(===(userId)), having(any[Some[Long]]), having(any[DateTime]), having(contain(
        anInstanceOf[UserConnected]
      )))
    }

    commandProcessor.connectWithFirebase(sessionId, authToken, email)
  }


  trait Context extends Scope {
    val userId: UUID = UUID.randomUUID()
    val dataStore: DataStore = mock[DataStore]
    val googleAuth = mock[GoogleAuthAdapter]
    val firebaseAuth = mock[EmailAuthProvider]
    implicit val ec = ExecutionContext.fromExecutor(new DeterministicExecutor)
    val commandProcessor: CommandProcessor = new UserCommandProcessor(dataStore, Nil, googleAuth, firebaseAuth)
    val sessionId: UUID = UUID.randomUUID()
    val facebookId = "facebook-id"
    val authToken = "auth-token"

    def ignoringGoogleUserData() = checking {
      ignoring(googleAuth).fetchAdditionalUserData(having(any), having(any)).willReturn(Right(GoogleUserData()))
    }

    def assumeExistingUser() = {
      assumeHasSequenceNum()
      checking {
        allowing(dataStore).userEvents(userId).willReturn(
          UserEventInstant(UserConnected(userId, DateTime.now().minusDays(1), UUID.randomUUID()), DateTime.now().minusDays(1)) :: Nil)
        allowing(dataStore).userBySession(sessionId).willReturn(Some(userId))
        allowing(dataStore).userIdByFacebookId(facebookId).willReturn(Some(userId))
      }
    }

    def assumeHasSequenceNum() = checking {
      allowing(dataStore).lastSequenceNum(userId).willReturn(Some(3L))
    }

    def assumeNoSuchUserForFacebookId() = checking {
      allowing(dataStore).userIdByFacebookId(facebookId).willReturn(None)
    }

    def processConnectWithFacebook() = {
      commandProcessor.process(ConnectFacebookUser(facebookId, authToken, sessionId))
    }

    def ignoringSaveUserEvents() = checking {
      ignoring(dataStore).saveUserEvents(having(any[UUID]), having(any), having(any[DateTime]), having(any[Seq[Event]])).willReturn(true)
    }

    def ignoringSaveUserSession() = checking {
      ignoring(dataStore).saveUserSession(having(any[UUID]), having(any[UUID]), having(any[DateTime]))
    }

    def assumeNewUser() = checking {
      allowing(dataStore).lastSequenceNum(having(any[UUID])).willReturn(None)
    }
  }

  trait EventProcessorContext extends Context {
    val eventProcessor: EventProcessor = mock[EventProcessor]
    override val commandProcessor = new UserCommandProcessor(dataStore, eventProcessor :: Nil, googleAuth, firebaseAuth)

    def ignoringProcessFacebookIdSet() = checking {
      ignoring(eventProcessor).process(having(any[UserFacebookIdSet]), having(any[UUID])).willReturn(Nil)
    }
  }

  trait ConnectContext extends Context {
    val googleUserId = "google user id"
    val email = "user@gmail.com"
    val givenName = "Bob"
    val familyName = "Hoskins"
    val name = s"$givenName $familyName"
    val photo = "https://lh4.googleusercontent.com/some-url/my-photo.jpg"
    val accessToken = "access-token"
    val idToken = "id-token"
    val locale = "en-US"
    val birthday = new LocalDate()
    val googleUser = GoogleUser(googleUserId, Option(email), Option(true), Option(name), Option(photo), Option(locale),
      Option(givenName), Option(familyName))

    checking {
      allowing(googleAuth).fetchAdditionalUserData(accessToken, googleUserId)
        .willReturn(Right(GoogleUserData(Option(birthday), Option(GenderData(Gender.Female)))))
    }
  }


  def aUserConnectedEventFor(userId: UUID): Matcher[UserConnected] = ===(userId) ^^ ((_: UserConnected).userId)
}

case class DummyCommand(eventsToReturn: List[UserEvent] = Nil) extends UserCommand {
  override def process(user: User): List[UserEvent] = eventsToReturn
}