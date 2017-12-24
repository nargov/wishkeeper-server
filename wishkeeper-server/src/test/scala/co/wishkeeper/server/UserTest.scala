package co.wishkeeper.server

import java.util.UUID
import java.util.UUID.randomUUID

import co.wishkeeper.server.Events._
import co.wishkeeper.server.EventsTestHelper.asEventInstant
import co.wishkeeper.server.FriendRequestStatus.Approved
import co.wishkeeper.server.NotificationsData.FriendRequestNotification
import co.wishkeeper.server.UserTestHelper._
import co.wishkeeper.test.utils.WishMatchers._
import com.wixpress.common.specs2.JMock
import org.joda.time.DateTime
import org.specs2.matcher.{Matcher, MatcherMacros}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import scala.language.experimental.macros

class UserTest extends Specification with MatcherMacros with JMock with NotificationMatchers {

  trait Context extends Scope {
    val user: User = User.createNew()
    val wish: Wish = Wish(randomUUID())
    val requestId: UUID = randomUUID()
    val friendId: UUID = randomUUID()

    def appliedEventCreatesExpectedWish(event: UserEvent, expectedWish: Wish): Any = {
      user.applyEvent(UserEventInstant(event, DateTime.now())).wishes(expectedWish.id) must beEqualToIgnoringDates(expectedWish)
    }

    val now: DateTime = DateTime.now()
  }


  "create a new user" in new Context {
    user must matchA[User].id(beAnInstanceOf[UUID])
  }

  "create a random id for a new user" in new Context {
    user.id must not(beEqualTo(User.createNew().id))
  }

  "apply UserFacebookIdSet event" in new Context {
    val facebookId = "facebook-id"
    user.applyEvent(UserEventInstant(UserFacebookIdSet(user.id, facebookId), now)).facebookId must beSome(facebookId)
  }

  "apply UserFirstNameSet" in new Context {
    val firstName = "George"
    user.applyEvent(UserEventInstant(UserFirstNameSet(user.id, firstName), now)).userProfile.firstName must beSome(firstName)
  }

  "apply UserLastNameSet" in new Context {
    val lastName = "Constanza"
    user.applyEvent(UserEventInstant(UserLastNameSet(user.id, lastName), now)).userProfile.lastName must beSome(lastName)
  }

  "apply UserNameSet" in new Context {
    val name = "George Constanza"
    user.applyEvent(UserEventInstant(UserNameSet(user.id, name), now)).userProfile.name must beSome(name)
  }

  "apply UserBirthdaySet" in new Context {
    val birthday = "05/13/1970"
    user.applyEvent(UserEventInstant(UserBirthdaySet(user.id, birthday), now)).userProfile.birthday must beSome(birthday)
  }

  "apply UserEmailSet" in new Context {
    val email = "abc@xyz.com"
    user.applyEvent(UserEventInstant(UserEmailSet(user.id, email), now)).userProfile.email must beSome(email)
  }

  "apply UserLocaleSet" in new Context {
    val locale = "en_UK"
    user.applyEvent(UserEventInstant(UserLocaleSet(user.id, locale), now)).userProfile.locale must beSome(locale)
  }

  "apply UserGenderSet" in new Context {
    val gender = "Female"
    user.applyEvent(UserEventInstant(UserGenderSet(user.id, gender), now)).userProfile.gender must beSome(gender)
  }

  "apply UserTimeZoneSet" in new Context {
    val timezone: Int = +3
    user.applyEvent(UserEventInstant(UserTimeZoneSet(user.id, timezone), now)).userProfile.timezone must beSome(timezone)
  }

  "apply UserAgeRangeSet" in new Context {
    val ageRange = AgeRange(Some(20), Some(30))
    user.applyEvent(UserEventInstant(UserAgeRangeSet(user.id, ageRange.min, ageRange.max), now)).userProfile.ageRange must beSome(ageRange)
  }

  "Recreate from Events" in new Context {
    val name = "Joe"
    val events = List(
      UserEventInstant(UserConnected(user.id, DateTime.now(), randomUUID()), DateTime.now()),
      UserEventInstant(UserNameSet(user.id, name), DateTime.now())
    )
    User.replay(events) must beEqualTo(User(user.id, UserProfile(name = Option(name))))
  }

  "throw exception if first event is not UserConnected" in {
    User.replay(Nil) must throwAn[IllegalArgumentException]
  }

  "apply FriendRequestSent" in new Context {
    user.withSentFriendRequest(requestId, friendId).friends.sentRequests must contain(FriendRequest(requestId, friendId, user.id))
  }

  "apply FriendRequestReceived" in new Context {
    user.withExistingFriendRequest(requestId, friendId).friends.receivedRequests must contain(FriendRequest(requestId, user.id, friendId))
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
    user
      .applyEvent(UserEventInstant(WishImageSet(wish.id, imageLinks), now))
      .applyEvent(UserEventInstant(WishImageDeleted(wish.id), now))
      .wishes(wish.id).image must beNone
  }

  "apply UserPictureSet" in new Context {
    private val pic = "picture-link"
    user.applyEvent(UserEventInstant(UserPictureSet(user.id, pic), now)).userProfile.picture must beSome(pic)
  }

  "apply WishCreated" in new Context {
    val creationTime: DateTime = DateTime.now()
    private val wishCreated = UserEventInstant(WishCreated(wish.id, user.id, creationTime), now)
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
    val imageLinks = ImageLinks(
      ImageLink("url", 30, 20, "image/jpeg") ::
        ImageLink("url", 10, 20, "image/jpeg") ::
        ImageLink("url", 20, 20, "image/jpeg") ::
        Nil)
    val sortedImageLinks = ImageLinks(
      ImageLink("url", 10, 20, "image/jpeg") ::
        ImageLink("url", 20, 20, "image/jpeg") ::
        ImageLink("url", 30, 20, "image/jpeg") ::
        Nil
    )
    user.applyEvent(UserEventInstant(WishImageSet(wish.id, imageLinks), now)).wishes(wish.id).image must beSome(sortedImageLinks)
  }

  "apply wish deleted" in new Context {
    user
      .applyEvent(UserEventInstant(WishCreated(wish.id, randomUUID(), DateTime.now()), now))
      .applyEvent(UserEventInstant(WishDeleted(wish.id), now))
      .wishes(wish.id).status must beEqualTo(WishStatus.Deleted)
  }

  "apply facebook friends list seen" in new Context {
    user.applyEvent(UserEventInstant(FacebookFriendsListSeen(), now)).seenFacebookFriends must beTrue
  }

  "have default seenFacebookFriends flag set to false" in new Context {
    user.seenFacebookFriends must beFalse
  }

  "apply FriendRequestNotificationCreated" in new Context {
    private val notificationId: UUID = randomUUID()
    user.
      applyEvent(UserEventInstant(FriendRequestNotificationCreated(notificationId, user.id, friendId, requestId), now)).
      notifications.head must beEqualTo(Notification(notificationId, FriendRequestNotification(friendId, requestId), time = now)
    )
  }

  "add friend to friends list when accepting" in new Context {
    user.withFriend(friendId).friends.current must contain(friendId)
  }

  "remove friend request when status changes" in new Context {
    user.withFriend(randomUUID()).friends.receivedRequests must beEmpty
  }

  "Change friend request notification status when request status changes" in new Context {
    user.withFriend(friendId, requestId).notifications must
      contain(aNotificationWith(aFriendRequestNotificationWithStatus(Approved)))
  }

  "remove friend request from sent list on change status" in new Context {
    user.withSentFriendRequestAccepted(requestId, friendId).friends.sentRequests must beEmpty
  }

  "add friend to friends list when accepted" in new Context {
    user.withSentFriendRequestAccepted(requestId, friendId).friends.current must contain(friendId)
  }

  "apply FriendRequestAcceptedNotificationCreated" in new Context {
    user.applyEvent(asEventInstant(FriendRequestAcceptedNotificationCreated(randomUUID(), user.id, friendId, requestId))).notifications must contain(
      aNotificationWith(aFriendRequestAcceptedNotification(requestId, friendId))
    )
  }

  "ignore FriendRequestReceived without id" in new Context {
    user.applyEvent(asEventInstant(FriendRequestReceived(user.id, friendId))).friends.current must beEmpty
  }

  "apply NotificationViewed" in new Context {
    val notificationId: UUID = randomUUID()
    user.withFriendRequestNotification(notificationId, requestId, friendId).
      applyEvent(asEventInstant(NotificationViewed(notificationId))).
      notifications.forall(_.viewed) must beTrue
  }

  "apply RemoveFriend" in new Context {
    user.withFriend(friendId).applyEvent(asEventInstant(FriendRemoved(user.id, friendId))).friends.current must not(contain(friendId))
  }

  def haveCreationTime(time: DateTime): Matcher[Wish] = ===(time) ^^ {
    (_: Wish).creationTime
  }
}



