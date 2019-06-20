package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.Events.UserEvent
import co.wishkeeper.server.user.{Gender, GenderPronoun, Platform}
import org.joda.time.DateTime

object Events {

  sealed trait UserEvent

  case object NoOp extends UserEvent

  case class EmailConnectStarted(userId: UUID) extends UserEvent

  case class UserConnected(userId: UUID, time: DateTime = DateTime.now(), sessionId: UUID) extends UserEvent

  case class UserFacebookIdSet(userId: UUID, facebookId: String) extends UserEvent

  case class UserFirstNameSet(userId: UUID, name: String) extends UserEvent

  case class UserLastNameSet(userId: UUID, name: String) extends UserEvent

  case class UserNameSet(userId: UUID, name: String) extends UserEvent

  case class UserBirthdaySet(userId: UUID, birthday: String) extends UserEvent

  case class UserAnniversarySet(date: String) extends UserEvent

  case class UserEmailSet(userId: UUID, email: String) extends UserEvent

  case class UserLocaleSet(userId: UUID, locale: String) extends UserEvent

  case class UserGenderSet(userId: UUID, gender: String) extends UserEvent

  case class UserGenderSet2(gender: Gender, customGender: Option[String], genderPronoun: Option[GenderPronoun]) extends UserEvent

  case class UserTimeZoneSet(userId: UUID, timezone: Int) extends UserEvent

  case class UserAgeRangeSet(userId: UUID, min: Option[Int], max: Option[Int]) extends UserEvent

  case class UserPictureSet(userId: UUID, pictureLink: String) extends UserEvent

  case object UserPictureDeleted extends UserEvent

  case class FriendRequestSent(userId: UUID, to: UUID, id: Option[UUID] = None) extends UserEvent

  case class FriendRequestReceived(userId: UUID, from: UUID, id: Option[UUID] = None) extends UserEvent

  case class FriendRequestStatusChanged(userId: UUID, requestId: UUID, from: UUID, status: FriendRequestStatus) extends UserEvent

  case class FriendRequestNotificationCreated(id: UUID, userId: UUID, from: UUID, requestId: UUID) extends UserEvent

  case class FriendRequestAcceptedNotificationCreated(id: UUID, userId: UUID, by: UUID, requestId: UUID) extends UserEvent

  case class FacebookFriendsListSeen(seen: Boolean = true) extends UserEvent

  case class GoogleFriendsListSeen(seen: Boolean = true) extends UserEvent

  case class FriendRemoved(userId: UUID, friendId: UUID) extends UserEvent

  case class NotificationViewed(id: UUID) extends UserEvent

  case class WishNameSet(wishId: UUID, name: String) extends UserEvent

  case class WishLinkSet(wishId: UUID, link: String) extends UserEvent

  case class WishStoreSet(wishId: UUID, store: String) extends UserEvent

  case class WishOtherInfoSet(wishId: UUID, otherInfo: String) extends UserEvent

  case class WishPriceSet(wishId: UUID, price: String) extends UserEvent

  case class WishCurrencySet(wishId: UUID, currency: String) extends UserEvent

  case class WishImageDeleted(wishId: UUID) extends UserEvent

  case class WishImageSet(wishId: UUID, imageLinks: ImageLinks) extends UserEvent

  case class WishCreated(wishId: UUID, createdBy: UUID, creationTime: DateTime) extends UserEvent

  case class WishDeleted(wishId: UUID) extends UserEvent

  case class WishGranted(wishId: UUID) extends UserEvent

  case class WishReserved(wishId: UUID, reserverId: UUID) extends UserEvent

  case class WishReservedNotificationCreated(id: UUID, wishId: UUID, reserverId: UUID) extends UserEvent

  case class WishUnreserved(wishId: UUID) extends UserEvent

  case class WishUnreservedNotificationCreated(id: UUID, wishId: UUID) extends UserEvent

  case class DeviceNotificationIdSet(id: String) extends UserEvent

  case class GeneralSettingPushNotificationEnabledSet(enabled: Boolean) extends UserEvent

  case class GeneralSettingVibrateEnabledSet(enabled: Boolean) extends UserEvent

  case class EmailVerified(email: String) extends UserEvent

  case class SessionPlatformSet(sessionId: UUID, platform: Platform) extends UserEvent
}

case class UserEventInstant[E <: UserEvent](event: E, time: DateTime)
object UserEventInstant {
  type UserEventInstants = List[UserEventInstant[_ <: UserEvent]]
}

case class UserEventInstance[E <: UserEvent](userId: UUID, event: E, time: DateTime = DateTime.now())
object UserEventInstance {
  def list(userId: UUID, time: DateTime, events: List[UserEvent]): List[UserEventInstance[UserEvent]] =
    events.map(UserEventInstance(userId, _, time))
}

