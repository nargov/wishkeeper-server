package co.wishkeeper.server

import java.util.UUID
import java.util.UUID.randomUUID

import co.wishkeeper.server.Events._
import com.wixpress.common.specs2.JMock
import org.joda.time.DateTime
import org.specs2.matcher.{Matcher, MatcherMacros}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import WishMatchers._

import scala.language.experimental.macros

class UserTest extends Specification with MatcherMacros with JMock {

  trait Context extends Scope {
    val user = User.createNew()
    val wish = Wish(randomUUID())

    def appliedEventCreatesExpectedWish(event: UserEvent, expectedWish: Wish): Any = {
      user.applyEvent(event).wishes(expectedWish.id) must beEqualToIgnoringDates(expectedWish)
    }
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
    appliedEventCreatesExpectedWish(WishNameSet(wish.id, name), wish.withName(name))

  }

  "apply WishLinkSet" in new Context {
    private val link = "link"
    appliedEventCreatesExpectedWish(WishLinkSet(wish.id, link), wish.withLink(link))
  }

  "apply WishStoreSet" in new Context {
    private val store = "store"
    appliedEventCreatesExpectedWish(WishStoreSet(wish.id, store), wish.withStore(store))
  }

  "apply WishOtherInfoSet" in new Context {
    private val otherInfo = "other info"
    appliedEventCreatesExpectedWish(WishOtherInfoSet(wish.id, otherInfo), wish.withOtherInfo(otherInfo))
  }

  "apply WishImageDeleted" in new Context {
    private val imageLinks = ImageLinks(ImageLink("", 0, 0, "") :: Nil)
    user.applyEvent(WishImageSet(wish.id, imageLinks)).applyEvent(WishImageDeleted(wish.id)).wishes(wish.id).image must beNone
  }

  "apply UserPictureSet" in new Context {
    private val pic = "picture-link"
    user.applyEvent(UserPictureSet(user.id, pic)).userProfile.picture must beSome(pic)
  }

  "apply WishCreated" in new Context {
    val creationTime = DateTime.now()
    private val wishCreated = WishCreated(wish.id, user.id, creationTime)
    user.applyEvent(wishCreated).wishes(wish.id) must haveCreationTime(creationTime)
  }

  "apply WishImageSet" in new Context {
    private val imageLinks = ImageLinks(ImageLink("url", 10, 20, "image/jpeg") :: Nil)
    appliedEventCreatesExpectedWish(WishImageSet(wish.id, imageLinks), wish.withImage(imageLinks))
  }

  "apply WishPriceSet" in new Context {
    val price = "12.34"
    appliedEventCreatesExpectedWish(WishPriceSet(wish.id, price), wish.withPrice(price))
  }

  "apply WishCurrencySet" in new Context {
    val currency = "GBP"
    appliedEventCreatesExpectedWish(WishCurrencySet(wish.id, currency), wish.withCurrency(currency))
  }

  "apply wish links ordered by width" in new Context {
    private val imageLinks = ImageLinks(
      ImageLink("url", 30, 20, "image/jpeg") ::
      ImageLink("url", 10, 20, "image/jpeg") ::
      ImageLink("url", 20, 20, "image/jpeg") ::
        Nil)
    user.applyEvent(WishImageSet(wish.id, imageLinks)).wishes(wish.id).image.get.links must beEqualTo(imageLinks.links.sortBy(_.width))
  }

  "apply wish deleted" in new Context {
    user.applyEvent(WishCreated(wish.id, randomUUID(), DateTime.now())).applyEvent(WishDeleted(wish.id)).wishes(wish.id).status must beEqualTo(WishStatus.Deleted)
  }

  def haveCreationTime(time: DateTime): Matcher[Wish] = ===(time) ^^ {(_:Wish).creationTime}
}
