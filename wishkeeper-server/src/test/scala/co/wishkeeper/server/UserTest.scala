package co.wishkeeper.server

import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

import co.wishkeeper.server.Commands.UserCommand
import co.wishkeeper.server.Events._
import com.wixpress.common.specs2.JMock
import org.specs2.matcher.MatcherMacros
import org.specs2.mutable.Specification
import User._
import org.joda.time.DateTime
import org.specs2.specification.Scope

import scala.language.experimental.macros

class UserTest extends Specification with MatcherMacros with JMock {

  trait Context extends Scope {
    val user = User.createNew()
  }

  "create a new user" in new Context {
    user must matchA[User].id(beAnInstanceOf[UUID])
  }

  "create a random id for a new user" in new Context {
    user.id must not(beEqualTo(User.createNew().id))
  }

  "apply UserFacebookIdSet event" in new Context {
    val facebookId = "facebook-id"
    user.applyEvent(UserFacebookIdSet(user.id, facebookId)).facebookId must beSome(facebookId)
  }

  "apply UserFirstNameSet" in new Context {
    val firstName = "George"
    user.applyEvent(UserFirstNameSet(user.id, firstName)).userProfile.firstName must beSome(firstName)
  }

  "apply UserLastNameSet" in new Context {
    val lastName = "Constanza"
    user.applyEvent(UserLastNameSet(user.id, lastName)).userProfile.lastName must beSome(lastName)
  }

  "apply UserNameSet" in new Context {
    val name = "George Constanza"
    user.applyEvent(UserNameSet(user.id, name)).userProfile.name must beSome(name)
  }

  "apply UserBirthdaySet" in new Context {
    val birthday = "05/13/1970"
    user.applyEvent(UserBirthdaySet(user.id, birthday)).userProfile.birthday must beSome(birthday)
  }

  "apply UserEmailSet" in new Context {
    val email = "abc@xyz.com"
    user.applyEvent(UserEmailSet(user.id, email)).userProfile.email must beSome(email)
  }

  "apply UserLocaleSet" in new Context {
    val locale = "en_UK"
    user.applyEvent(UserLocaleSet(user.id, locale)).userProfile.locale must beSome(locale)
  }

  "apply UserGenderSet" in new Context {
    val gender = "Female"
    user.applyEvent(UserGenderSet(user.id, gender)).userProfile.gender must beSome(gender)
  }

  "apply UserTimeZoneSet" in new Context {
    val timezone = +3
    user.applyEvent(UserTimeZoneSet(user.id, timezone)).userProfile.timezone must beSome(timezone)
  }

  "apply UserAgeRangeSet" in new Context {
    val ageRange = AgeRange(Some(20), Some(30))
    user.applyEvent(UserAgeRangeSet(user.id, ageRange.min, ageRange.max)).userProfile.ageRange must beSome(ageRange)
  }

  "Recreate from Events" in new Context {
    val name = "Joe"
    val events = List(UserConnected(user.id, DateTime.now(), UUID.randomUUID()), UserNameSet(user.id, name))
    User.replay(events) must beEqualTo(User(user.id, UserProfile(name = Option(name))))
  }
}
