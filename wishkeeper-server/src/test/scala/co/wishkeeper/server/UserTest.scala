package co.wishkeeper.server

import java.util.UUID
import java.util.UUID.randomUUID

import co.wishkeeper.server.Events._
import com.wixpress.common.specs2.JMock
import org.joda.time.DateTime
import org.specs2.matcher.MatcherMacros
import org.specs2.mutable.Specification
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
    val events = List(UserConnected(user.id, DateTime.now(), randomUUID()), UserNameSet(user.id, name))
    User.replay(events) must beEqualTo(User(user.id, UserProfile(name = Option(name))))
  }

  "throw exception if first event is not UserConnected" in {
    User.replay(Nil) must throwAn[IllegalArgumentException]
  }

  "apply FriendRequestSent" in new Context {
    private val potentialFriend = randomUUID()
    val friendRequest = FriendRequestSent(user.id, potentialFriend)
    user.applyEvent(friendRequest).friends.requestSent must contain(potentialFriend)
  }

  "apply FriendRequestReceived" in new Context {
    private val potentialFriend = randomUUID()
    val friendRequest = FriendRequestReceived(user.id, potentialFriend)
    user.applyEvent(friendRequest).friends.requestReceived must contain(potentialFriend)
  }

  "apply WishNameSet" in new Context {
    private val name = "name"
    val expectedWish = Wish(randomUUID(), name = Option(name))
    user.applyEvent(WishNameSet(expectedWish.id, name)).wishes must havePair(expectedWish.id -> expectedWish)
  }

  "apply WishLinkSet" in new Context {
    private val link = "link"
    val expectedWish = Wish(randomUUID(), link = Option(link))
    user.applyEvent(WishLinkSet(expectedWish.id, link)).wishes must havePair(expectedWish.id -> expectedWish)
  }

  "apply WishImageLinkSet" in new Context {
    private val imageLink = "image"
    val expectedWish = Wish(randomUUID(), imageLink = Option(imageLink))
    user.applyEvent(WishImageLinkSet(expectedWish.id, imageLink)).wishes must havePair(expectedWish.id -> expectedWish)
  }

  "apply WishStoreSet" in new Context {
    private val store = "store"
    val expectedWish = Wish(randomUUID(), store = Option(store))
    user.applyEvent(WishStoreSet(expectedWish.id, store)).wishes must havePair(expectedWish.id -> expectedWish)
  }

  "apply WishOtherInfoSet" in new Context {
    private val otherInfo = "other info"
    val expectedWish = Wish(randomUUID(), otherInfo = Option(otherInfo))
    user.applyEvent(WishOtherInfoSet(expectedWish.id, otherInfo)).wishes must havePair(expectedWish.id -> expectedWish)
  }
}
