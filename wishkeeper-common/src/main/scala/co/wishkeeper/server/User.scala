package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.Events._
import co.wishkeeper.server.UserEventInstant.UserEventInstants
import co.wishkeeper.server.WishStatus.{Active, Reserved, WishStatus}
import co.wishkeeper.server.user.events.FlagsHandlers._
import co.wishkeeper.server.user.events.FriendsEventHandlers._
import co.wishkeeper.server.user.events.NotificationEventHandlers._
import co.wishkeeper.server.user.events.ProfileEventHandlers._
import co.wishkeeper.server.user.events.SettingsEventHandlers._
import co.wishkeeper.server.user.events.UserEventHandler
import co.wishkeeper.server.user.events.WishlistEventHandlers._
import org.joda.time.DateTime


case class User(id: UUID,
                userProfile: UserProfile = UserProfile(),
                friends: Friends = Friends(),
                wishes: Map[UUID, Wish] = Map.empty,
                flags: Flags = Flags(),
                notifications: List[Notification] = Nil,
                pendingNotifications: List[Notification] = Nil,
                settings: Settings = Settings(),
                created: DateTime = DateTime.now(),
                lastWishlistChange: Option[DateTime] = None) {

  def applyEvent[E <: UserEvent](event: UserEventInstant[E]): User = event match {
    case UserEventInstant(e@UserFirstNameSet(_, value), time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@UserLastNameSet(_, value), time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@UserNameSet(_, value), time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@UserBirthdaySet(_, value), time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@UserEmailSet(_, value), time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@UserLocaleSet(_, value), time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@UserGenderSet(_, _), time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@UserTimeZoneSet(_, value), time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@UserAgeRangeSet(_, min, max), time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@UserFacebookIdSet(_, fbId), time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@UserPictureSet(_, link), time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@FriendRequestSent(_, friendId, reqId), time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@FriendRequestReceived(_, friendId, reqId), time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@WishCreated(_, _, _), time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@WishNameSet(_, _), time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@WishLinkSet(_, _), time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@WishPriceSet(_, _), time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@WishCurrencySet(_, _), time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@WishStoreSet(_, _), time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@WishOtherInfoSet(wishId, info), time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@WishImageSet(wishId, imageLinks), time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@WishImageDeleted(wishId), time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@WishDeleted(_), time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@WishGranted(wishId), time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@WishReserved(wishId, reserver), time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@WishUnreserved(wishId), time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@FriendRequestNotificationCreated(notificationId, _, from, reqId), time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@FriendRequestStatusChanged(_, reqId, from, toStatus), time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@FriendRequestAcceptedNotificationCreated(notificationId, _, by, requestId), time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@NotificationViewed(notificationId), time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@FriendRemoved(_, friendId), time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@FacebookFriendsListSeen(_), time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@GoogleFriendsListSeen(_), time) => handleEventWithHandler(e, time)
    case UserEventInstant(n@WishReservedNotificationCreated(_, _, _), time) => handleEventWithHandler(n, time)
    case UserEventInstant(e@WishUnreservedNotificationCreated(_, _), time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@DeviceNotificationIdSet(_), time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@UserPictureDeleted, time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@UserGenderSet2(_, _, _), time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@GeneralSettingPushNotificationEnabledSet(_), time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@GeneralSettingVibrateEnabledSet(_), time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@UserAnniversarySet(_), time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@EmailConnectStarted(_), time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@EmailVerified(_), time) => handleEventWithHandler(e, time)
    case UserEventInstant(e@UserConnected(_, _, _), time) => handleEventWithHandler(e, time)
    case _ => this
  }

  private def handleEventWithHandler[E <: UserEvent](event: E, time: DateTime)(implicit handler: UserEventHandler[E]): User =
    handler(this, event, time)

  def seenFacebookFriends: Boolean = flags.seenFacebookFriendsList

  def hasFriend(friendId: UUID): Boolean = friends.current.contains(friendId)

  def hasPendingFriend(friendId: UUID): Boolean =
    friends.sentRequests.exists(_.userId == friendId) ||
      friends.receivedRequests.exists(_.from == friendId)

  lazy val facebookId: Option[String] = userProfile.socialData.flatMap(_.facebookId)

  def friendRequestId(friendId: UUID): Option[UUID] = friends.receivedRequests.find(_.from == friendId).map(_.id)

  private val isShownInWishlist: WishStatus => Boolean = {
    case Active | Reserved(_) => true
    case _ => false
  }

  def shownWishesByDate: List[Wish] = wishes.values.toList.
    filter(w => isShownInWishlist(w.status)).
    sortBy(_.creationTime.getMillis).
    reverse

  def activeWishesByDate: List[Wish] = wishes.values.toList.
    filter(_.status == Active).
    sortBy(_.creationTime.getMillis).
    reverse
}

object User {
  def replay(events: UserEventInstants): User = {
    events match {
      case UserEventInstant(UserConnected(userId, _, _), time) :: _ =>
        events.foldLeft(User(userId, created = time))((user, eventInstant) => user.applyEvent(eventInstant))
      case UserEventInstant(EmailConnectStarted(userId), time) :: _ =>
        events.foldLeft(User(userId, created = time))((user, eventInstant) => user.applyEvent(eventInstant))
      case _ => throw new IllegalArgumentException("Invalid or corrupted user event stream")
    }
  }

  def createNew() = new User(UUID.randomUUID())
}

case class Friends(current: List[UUID] = Nil,
                   sentRequests: List[FriendRequest] = Nil,
                   receivedRequests: List[FriendRequest] = Nil)

case class Flags(seenFacebookFriendsList: Boolean = false, seenGoogleFriendsList: Boolean = false, haveOpenEmailConnect: Boolean = false,
                 emailVerified: Boolean = false, everConnected: Boolean = false) {

}

case class Settings(deviceNotificationId: Option[String] = None, general: GeneralSettings = GeneralSettings())

case class GeneralSettings(pushNotificationsEnabled: Boolean = true, vibrate: Boolean = true)

