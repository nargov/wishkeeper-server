package co.wishkeeper.server

import java.util.UUID

import org.joda.time.DateTime

object Events {

  sealed trait Event

  sealed trait UserEvent extends Event

  case object NoOp extends UserEvent

  case class UserConnected(userId: UUID, time: DateTime = DateTime.now(), sessionId: UUID) extends UserEvent

  case class UserFacebookIdSet(userId: UUID, facebookId: String) extends UserEvent

  case class UserFirstNameSet(userId: UUID, name: String) extends UserEvent

  case class UserLastNameSet(userId: UUID, name: String) extends UserEvent

  case class UserNameSet(userId: UUID, name: String) extends UserEvent

  case class UserBirthdaySet(userId: UUID, birthday: String) extends UserEvent

  case class UserEmailSet(userId: UUID, email: String) extends UserEvent

  case class UserLocaleSet(userId: UUID, locale: String) extends UserEvent

  case class UserGenderSet(userId: UUID, gender: String) extends UserEvent

  case class UserTimeZoneSet(userId: UUID, timezone: Int) extends UserEvent

  case class UserAgeRangeSet(userId: UUID, min: Option[Int], max: Option[Int]) extends UserEvent

  case class FriendRequestSent(userId: UUID, to: UUID) extends UserEvent

  case class FriendRequestReceived(userId: UUID, from: UUID) extends UserEvent

  case class WishNameSet(wishId: UUID, name: String) extends UserEvent

  case class WishLinkSet(wishId: UUID, link: String) extends UserEvent

  case class WishImageLinkSet(wishId: UUID, link: String) extends UserEvent

  case class WishStoreSet(wishId: UUID, store: String) extends UserEvent

  case class WishOtherInfoSet(wishId: UUID, otherInfo: String) extends UserEvent

  case class WishPriceSet(wishId: UUID, price: String) extends UserEvent

  case class WishCurrencySet(wishId: UUID, currency: String) extends UserEvent

  case class WishImageDeleted(wishId: UUID) extends UserEvent
}