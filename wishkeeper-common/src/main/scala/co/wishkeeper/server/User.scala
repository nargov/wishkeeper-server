package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.Events._
import co.wishkeeper.server.FriendRequestStatus.{Approved, Pending}
import co.wishkeeper.server.NotificationsData.{FriendRequestAcceptedNotification, FriendRequestNotification}
import co.wishkeeper.server.UserEventInstant.UserEventInstants
import co.wishkeeper.server.WishStatus.{Active, Reserved, WishStatus}
import co.wishkeeper.server.user.events.NotificationEventHandlers._
import co.wishkeeper.server.user.events.SettingsEventHandlers._
import co.wishkeeper.server.user.events.ProfileEventHandlers._
import co.wishkeeper.server.user.events.UserEventHandler
import co.wishkeeper.server.user.events.FlagsHandlers._
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
    case UserEventInstant(UserFirstNameSet(_, value), _) => this.copy(userProfile = this.userProfile.copy(firstName = Option(value)))
    case UserEventInstant(UserLastNameSet(_, value), _) => this.copy(userProfile = this.userProfile.copy(lastName = Option(value)))
    case UserEventInstant(UserNameSet(_, value), _) => this.copy(userProfile = this.userProfile.copy(name = Option(value)))
    case UserEventInstant(UserBirthdaySet(_, value), _) => this.copy(userProfile = this.userProfile.copy(birthday = Option(value)))
    case UserEventInstant(UserEmailSet(_, value), _) => this.copy(userProfile = this.userProfile.copy(email = Option(value)))
    case UserEventInstant(UserLocaleSet(_, value), _) => this.copy(userProfile = this.userProfile.copy(locale = Option(value)))
    case UserEventInstant(e@UserGenderSet(_, _), time) => handleEventWithHandler(e, time)
    case UserEventInstant(UserTimeZoneSet(_, value), _) => this.copy(userProfile = this.userProfile.copy(timezone = Option(value)))
    case UserEventInstant(UserAgeRangeSet(_, min, max), _) => this.copy(userProfile = this.userProfile.copy(ageRange = Option(AgeRange(min, max))))
    case UserEventInstant(UserFacebookIdSet(_, fbId), _) => this.copy(userProfile = this.userProfile.copy(socialData =
      this.userProfile.socialData match {
        case Some(data) => Option(data.copy(facebookId = Option(fbId)))
        case None => Option(SocialData(Option(fbId)))
      }))
    case UserEventInstant(UserPictureSet(_, link), _) => this.copy(userProfile = userProfile.copy(picture = Option(link)))
    case UserEventInstant(FriendRequestSent(_, friendId, reqId), _) => reqId.map(requestId => this.copy(
      friends = this.friends.copy(sentRequests = this.friends.sentRequests :+ FriendRequest(requestId, friendId, id)))).getOrElse(this)
    case UserEventInstant(FriendRequestReceived(_, friendId, reqId), _) => reqId.map(requestId => this.copy(
      friends = this.friends.copy(receivedRequests = this.friends.receivedRequests :+ FriendRequest(requestId, id, friendId)))).getOrElse(this)
    case UserEventInstant(e@WishCreated(_, _, _), time) => handleEventWithHandler(e, time)
    case UserEventInstant(WishNameSet(wishId, name), _) => updateWishProperty(wishId, _.withName(name))
    case UserEventInstant(WishLinkSet(wishId, link), _) => updateWishProperty(wishId, _.withLink(link))
    case UserEventInstant(WishPriceSet(wishId, price), _) => updateWishProperty(wishId, _.withPrice(price))
    case UserEventInstant(WishCurrencySet(wishId, currency), _) => updateWishProperty(wishId, _.withCurrency(currency))
    case UserEventInstant(WishStoreSet(wishId, store), _) => updateWishProperty(wishId, _.withStore(store))
    case UserEventInstant(WishOtherInfoSet(wishId, info), _) => updateWishProperty(wishId, _.withOtherInfo(info))
    case UserEventInstant(WishImageSet(wishId, imageLinks), _) => updateWishProperty(wishId, _.withImage(imageLinks))
    case UserEventInstant(WishImageDeleted(wishId), _) => updateWishProperty(wishId, _.withoutImage)
    case UserEventInstant(e@WishDeleted(_), time) => handleEventWithHandler(e, time)
    case UserEventInstant(WishGranted(wishId), time) =>
      val granter = wishes(wishId).status match {
        case WishStatus.Reserved(reserver) => Option(reserver)
        case _ => None
      }
      updateWishProperty(wishId, _.withStatus(WishStatus.Granted(granter), time))
    case UserEventInstant(WishReserved(wishId, reserver), time) =>
      updateWishProperty(wishId, _.withStatus(WishStatus.Reserved(reserver), time).withReserver(reserver))
    case UserEventInstant(WishUnreserved(wishId), time) =>
      updateWishProperty(wishId, _.withStatus(WishStatus.Active, time).withNoReserver)
    case UserEventInstant(FriendRequestNotificationCreated(notificationId, _, from, reqId), time) => this.copy(
      notifications = Notification(notificationId, FriendRequestNotification(from, reqId), time = time) :: notifications)
    case UserEventInstant(FriendRequestStatusChanged(_, reqId, from, toStatus), _) => this.copy(
      friends = friends.copy(
        receivedRequests = if (from != id) friends.receivedRequests.filterNot(_.id == reqId) else friends.receivedRequests,
        sentRequests = if (from == id) friends.sentRequests.filterNot(_.id == reqId) else friends.sentRequests,
        current = toStatus match {
          case Approved => if (from == id) {
            friends.sentRequests.find(_.id == reqId).map(_.userId).map(friends.current :+ _).getOrElse(friends.current)
          } else friends.current :+ from
          case _ => friends.current
        }),
      notifications = notifications.map {
        case n@Notification(_, notif@FriendRequestNotification(_, friendReqId, status, _), _, _) if friendReqId == reqId && status == Pending =>
          n.copy(data = notif.copy(status = toStatus))
        case n => n
      }
    )
    case UserEventInstant(FriendRequestAcceptedNotificationCreated(notificationId, _, by, requestId), time) => this.copy(
      notifications = Notification(notificationId, FriendRequestAcceptedNotification(by, requestId), time = time) :: notifications)
    case UserEventInstant(NotificationViewed(notificationId), _) =>
      val index = notifications.indexWhere(_.id == notificationId)
      if (index >= 0) {
        val updatedNotification = notifications(index).copy(viewed = true)
        this.copy(notifications = notifications.updated(index, updatedNotification))
      }
      else this
    case UserEventInstant(FriendRemoved(_, friendId), _) => this.copy(friends = friends.copy(current = friends.current.filterNot(_ == friendId)))

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

  private def updateWishProperty(wishId: UUID, updater: Wish => Wish) =
    this.copy(wishes = wishes + (wishId -> updater(wishes.getOrElse(wishId, Wish(wishId)))))

  lazy val facebookId: Option[String] = userProfile.socialData.flatMap(_.facebookId)

  def friendRequestId(friendId: UUID): Option[UUID] = friends.receivedRequests.find(_.from == friendId).map(_.id)

  private val isShownInWishlist: WishStatus => Boolean = {
    case Active | Reserved(_) => true
    case _ => false
  }

  def shownWishesByDate = wishes.values.toList.
    filter(w => isShownInWishlist(w.status)).
    sortBy(_.creationTime.getMillis).
    reverse

  def activeWishesByDate = wishes.values.toList.
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

