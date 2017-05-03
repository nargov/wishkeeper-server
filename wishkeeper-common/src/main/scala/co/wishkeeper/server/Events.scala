package co.wishkeeper.server

import java.util.UUID

import org.joda.time.DateTime

object Events {

  sealed trait Event

  sealed trait UserEvent extends Event

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

  case object NoOp extends UserEvent
}