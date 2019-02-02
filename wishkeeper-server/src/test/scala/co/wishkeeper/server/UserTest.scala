package co.wishkeeper.server

import java.util.UUID
import java.util.UUID.randomUUID

import co.wishkeeper.server.Events._
import co.wishkeeper.server.EventsTestHelper.{EventsList, asEventInstant}
import co.wishkeeper.server.FriendRequestStatus.Approved
import co.wishkeeper.server.NotificationsData.{FriendRequestNotification, WishReservedNotification, WishUnreservedNotification}
import co.wishkeeper.server.UserTestHelper._
import co.wishkeeper.server.WishStatus.WishStatus
import co.wishkeeper.server.user.{Gender, GenderPronoun}
import co.wishkeeper.test.utils.WishMatchers._
import com.wixpress.common.specs2.JMock
import org.joda.time.DateTime
import org.specs2.matcher.{Matcher, MatcherMacros}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import scala.language.experimental.macros

class UserTest extends Specification with MatcherMacros with JMock with NotificationMatchers {

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
    val time: DateTime = DateTime.now()
    val events = List(
      UserEventInstant(UserConnected(user.id, time, randomUUID()), time),
      UserEventInstant(UserNameSet(user.id, name), time)
    )
    User.replay(events) must beEqualTo(User(user.id, created = time, userProfile = UserProfile(name = Option(name)),
      flags = Flags(everConnected = true)))
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
    appliedEventCreatesExpectedWish(WishNameSet(wishId, name), wish.withName(name))

  }

  "apply WishLinkSet" in new Context {
    private val link = "link"
    appliedEventCreatesExpectedWish(WishLinkSet(wishId, link), wish.withLink(link))
  }

  "apply WishStoreSet" in new Context {
    private val store = "store"
    appliedEventCreatesExpectedWish(WishStoreSet(wishId, store), wish.withStore(store))
  }

  "apply WishOtherInfoSet" in new Context {
    private val otherInfo = "other info"
    appliedEventCreatesExpectedWish(WishOtherInfoSet(wishId, otherInfo), wish.withOtherInfo(otherInfo))
  }

  "apply WishImageDeleted" in new Context {
    private val imageLinks = ImageLinks(ImageLink("", 0, 0, "") :: Nil)
    user
      .applyEvent(UserEventInstant(WishImageSet(wishId, imageLinks), now))
      .applyEvent(UserEventInstant(WishImageDeleted(wishId), now))
      .wishes(wishId).image must beNone
  }

  "apply UserPictureSet" in new Context {
    private val pic = "picture-link"
    user.applyEvent(UserEventInstant(UserPictureSet(user.id, pic), now)).userProfile.picture must beSome(pic)
  }

  "apply WishCreated" in new Context {
    val creationTime: DateTime = DateTime.now()
    private val wishCreated = UserEventInstant(WishCreated(wishId, user.id, creationTime), now)
    user.applyEvent(wishCreated).wishes(wishId) must haveCreationTime(creationTime)
  }

  "apply WishImageSet" in new Context {
    private val imageLinks = ImageLinks(ImageLink("url", 10, 20, "image/jpeg") :: Nil)
    appliedEventCreatesExpectedWish(WishImageSet(wishId, imageLinks), wish.withImage(imageLinks))
  }

  "apply WishPriceSet" in new Context {
    val price = "12.34"
    appliedEventCreatesExpectedWish(WishPriceSet(wishId, price), wish.withPrice(price))
  }

  "apply WishCurrencySet" in new Context {
    val currency = "GBP"
    appliedEventCreatesExpectedWish(WishCurrencySet(wishId, currency), wish.withCurrency(currency))
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
    user.applyEvent(UserEventInstant(WishImageSet(wishId, imageLinks), now)).wishes(wishId).image must beSome(sortedImageLinks)
  }

  "apply wish deleted" in new Context {
    user
      .applyEvent(UserEventInstant(WishCreated(wishId, randomUUID(), DateTime.now()), now))
      .applyEvent(UserEventInstant(WishDeleted(wishId), now))
      .wishes(wishId).status must beEqualTo(WishStatus.Deleted)
  }

  "apply facebook friends list seen" in new Context {
    user.applyEvent(UserEventInstant(FacebookFriendsListSeen(), now)).seenFacebookFriends must beTrue
  }

  "have default seenFacebookFriends flag set to false" in new Context {
    user.seenFacebookFriends must beFalse
  }

  "apply FriendRequestNotificationCreated" in new Context {
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
    user.withFriendRequestNotification(notificationId, requestId, friendId).
      applyEvent(asEventInstant(NotificationViewed(notificationId))).
      notifications.forall(_.viewed) must beTrue
  }

  "apply RemoveFriend" in new Context {
    user.withFriend(friendId).applyEvent(asEventInstant(FriendRemoved(user.id, friendId))).friends.current must not(contain(friendId))
  }

  "apply WishGranted" in new Context {
    val updatedWish = user.withWish(wishId).applyEvent(asEventInstant(WishGranted(wishId), now)).wishes(wishId)
    updatedWish must haveStatus(WishStatus.Granted()) and haveStatusLastUpdate(now)
  }

  "apply WishReserved" in new Context {
    val updatedWish = user.withWish(wishId).applyEvent(asEventInstant(WishReserved(wishId, friendId), now)).wishes(wishId)
    updatedWish must haveStatus(WishStatus.Reserved(friendId)) and haveStatusLastUpdate(now)
  }

  "denote granted wish with the reserver" in new Context {
    val userWithGrantedWIsh: User = user.withReservedWish(wishId, friendId).applyEvent(asEventInstant(WishGranted(wishId)))
    userWithGrantedWIsh.wishes.values must contain(aGrantedWish(wishId, friendId))
  }

  "apply WishUnreserved" in new Context {
    val updatedWish = user.withReservedWish(wishId, friendId).applyEvent(asEventInstant(WishUnreserved(wishId), now)).wishes(wishId)
    updatedWish must haveStatus(WishStatus.Active) and haveStatusLastUpdate(now)
  }

  "create WishReservedNotification after the time delay" in new Context {
    val notifications = user.withReservedWish(wishId, friendId).
      applyEvent(asEventInstant(WishReservedNotificationCreated(notificationId, wishId, friendId), DateTime.now().minusMinutes(6))).notifications
    notifications must contain(aNotificationWith(===(WishReservedNotification(wishId, friendId))))
  }

  "not create a WishReserveNotification when minimum time is below threshold" in new Context {
    val notifications = user.withReservedWish(wishId, friendId).
      applyEvent(asEventInstant(WishReservedNotificationCreated(notificationId, wishId, friendId))).notifications
    notifications must not(contain(aNotificationType[WishReservedNotificationCreated]))
  }

  "not create a WishReservedNotification when wish was unreserved before time threshold" in new Context {
    val notifications = user.withReservedWish(wishId, friendId).
      applyEvent(asEventInstant(WishReservedNotificationCreated(notificationId, wishId, friendId))).notifications
    notifications must not(contain(aNotificationType[WishReservedNotificationCreated]))
  }

  "create WishUnreservedNotification after time delay" in new Context {
    val notifications = user.withReservedWish(wishId, friendId).
      applyEvent(asEventInstant(WishReservedNotificationCreated(notificationId, wishId, friendId), DateTime.now().minusMinutes(12))).
      applyEvent(asEventInstant(WishUnreserved(wishId), DateTime.now().minusMinutes(6))).
      applyEvent(asEventInstant(WishUnreservedNotificationCreated(notificationId, wishId), DateTime.now().minusMinutes(6))).
      notifications
    notifications must contain(aNotificationWith(===(WishUnreservedNotification(wishId, friendId))))
  }

  "not create WishUnreservedNotification when time delay below threshold" in new Context {
    val notifications = user.withReservedWish(wishId, friendId).
      applyEvent(asEventInstant(WishUnreserved(wishId), DateTime.now().minusMinutes(4))).
      applyEvent(asEventInstant(WishUnreservedNotificationCreated(notificationId, wishId), DateTime.now().minusMinutes(4))).
      notifications
    notifications must not(contain(aNotificationType[WishUnreservedNotification]))
  }

  "not create WishReservedNotification and WishUnreservedNotification if time between is below threshold" in new Context {
    val notifications = user.withReservedWish(wishId, friendId).
      applyEvent(asEventInstant(WishReservedNotificationCreated(notificationId, wishId, friendId), DateTime.now().minusMinutes(10))).
      applyEvent(asEventInstant(WishUnreserved(wishId), DateTime.now().minusMinutes(8))).
      applyEvent(asEventInstant(WishUnreservedNotificationCreated(notificationId, wishId), DateTime.now().minusMinutes(8))).
      notifications
    notifications must not(contain(aNotificationType[WishReservedNotification])) and not(contain(aNotificationType[WishUnreservedNotification]))
  }

  "create WishReservedNotification and WishUnreservedNotification only beyond threshold" in new Context {
    val firstReserveTime: DateTime = DateTime.now().minusMinutes(20)
    val firstUnreserveTime: DateTime = firstReserveTime.plusMinutes(6)
    val secondReserveTime: DateTime = firstUnreserveTime.plusMinutes(1)
    val secondUnreserveTime: DateTime = secondReserveTime.plusMinutes(1)
    val notifId1 = randomUUID()
    val notifId2 = randomUUID()
    val notifId3 = randomUUID()
    val notifId4 = randomUUID()
    val notifications = user.
      applyEvent(asEventInstant(WishReservedNotificationCreated(notifId1, wishId, friendId), firstReserveTime)).
      applyEvent(asEventInstant(WishUnreservedNotificationCreated(notifId2, wishId), firstUnreserveTime)).
      applyEvent(asEventInstant(WishReservedNotificationCreated(notifId3, wishId, friendId), secondReserveTime)).
      applyEvent(asEventInstant(WishUnreservedNotificationCreated(notifId4, wishId), secondUnreserveTime)).
      notifications

    notifications must
      have size 2 and
      contain(aNotificationWithId(notifId1)) and
      contain(aNotificationWithId(notifId4)) and
      not(contain(aNotificationWithId(notifId2))) and
      not(contain(aNotificationWithId(notifId3)))
  }

  "ignore notification-viewed event if notification was discarded" in new Context {
    user.applyEvent(asEventInstant(NotificationViewed(notificationId))).notifications must beEmpty
  }

  "discard WishReservedNotification if WishUnreservedNotification exists within threshold" in new Context {
    val reserveTime = DateTime.now().minusMinutes(6)
    val unreserveTime = reserveTime.plusMinutes(3)
    val notifId1 = randomUUID()
    val notifId2 = randomUUID()

    val notifications = user.
      applyEvent(asEventInstant(WishReservedNotificationCreated(notifId1, wishId, friendId), reserveTime)).
      applyEvent(asEventInstant(WishUnreservedNotificationCreated(notifId2, wishId), unreserveTime)).
      notifications

    notifications must beEmpty
  }

  "discard WishUnreservedNotification if WishReservedNotification exists within threshold" in new Context {
    val reserveTime = DateTime.now().minusMinutes(20)
    val unreserveTime = reserveTime.plusMinutes(10)
    val reReserveTime = unreserveTime.plusMinutes(3)
    val notifications: List[Notification] = user.
      applyEvent(asEventInstant(WishReservedNotificationCreated(randomUUID(), wishId, friendId), reserveTime)).
      applyEvent(asEventInstant(WishUnreservedNotificationCreated(randomUUID(), wishId), unreserveTime)).
      applyEvent(asEventInstant(WishReservedNotificationCreated(randomUUID(), wishId, friendId), reReserveTime)).
      notifications

    notifications must have size 1
  }

  "return incoming friend request id" in new Context {

    user.applyEvent(asEventInstant(FriendRequestReceived(user.id, friendId, Option(requestId)))).friendRequestId(friendId) must beSome(requestId)
  }

  "return pending Wish reserved notification" in new Context {
    val time = DateTime.now().minusMinutes(2)
    user.applyEvent(asEventInstant(WishReservedNotificationCreated(notificationId, wishId, friendId), time)).
      pendingNotifications must beEqualTo(List(Notification(notificationId, WishReservedNotification(wishId, friendId), time = time)))
  }

  "return pending wish unreserved notification" in new Context {
    val time = DateTime.now().minusMinutes(2)
    user.
      applyEvent(asEventInstant(WishReservedNotificationCreated(randomUUID(), wishId, friendId), time.minusMinutes(20))).
      applyEvent(asEventInstant(WishUnreservedNotificationCreated(notificationId, wishId), time)).
      pendingNotifications must beEqualTo(List(Notification(notificationId, WishUnreservedNotification(wishId, friendId), time = time)))
  }

  "apply DeviceNotificationIdSet" in new Context {
    val id = "expected-id"
    user.applyEvent(asEventInstant(DeviceNotificationIdSet(id))).settings.deviceNotificationId must beSome(id)
  }

  "apply UserPictureDeleted" in new Context {
    val pictureSet: UserEventInstant[UserEvent] = asEventInstant(UserPictureSet(user.id, "some link"))
    val pictureDeleted: UserEventInstant[UserEvent] = asEventInstant(UserPictureDeleted)

    user.applyEvent(pictureSet).applyEvent(pictureDeleted).userProfile.picture must beNone
  }

  "apply UserGenderSet2" in new Context {
    val genderSet = UserGenderSet2(Gender.Custom, Option("Meat Popsicle"), Option(GenderPronoun.Neutral))
    user.applyEvent(asEventInstant(genderSet)).userProfile.genderData must beSome(
      GenderData(Gender.Custom, Option("Meat Popsicle"), Option(GenderPronoun.Neutral))
    )
  }

  "apply GeneralSettingPushNotificationEnabledSet" in new Context {
    user.applyEvent(asEventInstant(GeneralSettingPushNotificationEnabledSet(false))).settings.general.pushNotificationsEnabled must beFalse
  }

  "apply GeneralSettingVibrateEnabledSet" in new Context {
    user.applyEvent(asEventInstant(GeneralSettingVibrateEnabledSet(false))).settings.general.vibrate must beFalse
  }

  "apply UserAnniversarySet" in new Context {
    val date = "03/04/2000"
    user.applyEvent(asEventInstant(UserAnniversarySet(date))).userProfile.anniversary must beSome(date)
  }

  "set reserver on reserved wish" in new Context {
    user.withReservedWish(wishId, friendId).wishes(wishId).reserver must beSome(friendId)
  }

  "remove reserver on unreserved wish" in new Context {
    user.withReservedWish(wishId, friendId).withEvent(WishUnreserved(wishId)).wishes(wishId).reserver must beNone
  }

  "set last reserver on reserved wish" in new Context {
    user.withReservedWish(wishId, friendId).wishes(wishId).pastReservers must contain(friendId)
  }

  "apply GoogleFriendsListSeen" in new Context {
    user.applyEvent(asEventInstant(GoogleFriendsListSeen())).flags.seenGoogleFriendsList must beTrue
  }

  "apply EmailConnectStarted" in new Context {
    user.applyEvent(asEventInstant(EmailConnectStarted(user.id))).flags.haveOpenEmailConnect must beTrue
  }

  "apply EmailVerified" in new Context {
    val email = "user@address.com"
    val events = EventsList(user.id).withEmail(email)
      .withEvent(EmailConnectStarted(user.id))
      .withEvent(EmailVerified(email))
      .list
    val flags: Flags = events.foldLeft(user)(_.applyEvent(_)).flags
    flags.haveOpenEmailConnect must beFalse
    flags.emailVerified must beTrue
  }

  "User Connected event marks flag" in new Context {
    EventsList(user.id).list.foldLeft(user)(_.applyEvent(_)).flags.everConnected must beTrue
  }

  "Return last wishlist change based on wish added" in new Context {
    val creationTime: DateTime = DateTime.now().minusMinutes(5)
    user.applyEvent(asEventInstant(WishCreated(wishId, user.id, creationTime), creationTime)).lastWishlistChange must beSome(creationTime)
  }

  "Return last wishlist change based on wish deleted" in new Context {
    val time: DateTime = DateTime.now().minusMinutes(5)
    user.applyEvent(asEventInstant(WishDeleted(wishId), time)).lastWishlistChange must beSome(time)
  }

  "Return correct GenderData when only Gender string is set" in new Context {
    user.withEvent(UserGenderSet(user.id, "male")).userProfile.genderData must beSome(GenderData(Gender.Male))
    user.withEvent(UserGenderSet(user.id, "female")).userProfile.genderData must beSome(GenderData(Gender.Female))
    user.withEvent(UserGenderSet(user.id, "other")).userProfile.genderData must beSome(GenderData(Gender.Custom))
  }

  trait Context extends Scope {
    val user: User = User.createNew()
    val wish: Wish = Wish(randomUUID())
    val wishId = wish.id
    val requestId: UUID = randomUUID()
    val friendId: UUID = randomUUID()
    val notificationId = randomUUID()

    def appliedEventCreatesExpectedWish(event: UserEvent, expectedWish: Wish): Any = {
      user.applyEvent(UserEventInstant(event, DateTime.now())).wishes(expectedWish.id) must beEqualToIgnoringDates(expectedWish)
    }

    val now: DateTime = DateTime.now()
  }

  def haveStatusLastUpdate(time: DateTime): Matcher[Wish] = beSome(time) ^^ ((_: Wish).statusLastUpdate)

  def haveStatus(status: WishStatus): Matcher[Wish] = ===(status) ^^ ((_: Wish).status)

  def haveCreationTime(time: DateTime): Matcher[Wish] = ===(time) ^^ {
    (_: Wish).creationTime
  }
}
