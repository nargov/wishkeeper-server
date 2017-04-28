package co.wishkeeper.server

import java.util.UUID

import org.joda.time.DateTime

object Events {

  sealed trait UserEvent

  case class UserConnected(time: DateTime, sessionId: UUID) extends UserEvent

  case class UserFacebookIdSet(facebookId: String) extends UserEvent

  case class UserFirstNameSet(name: String) extends UserEvent

  case class UserLastNameSet(name: String) extends UserEvent

  case class UserNameSet(name: String) extends UserEvent

  case class UserBirthdaySet(birthday: String) extends UserEvent

  case class UserEmailSet(email: String) extends UserEvent

  case class UserLocaleSet(locale: String) extends UserEvent

  case class UserGenderSet(gender: String) extends UserEvent

  case class UserTimeZoneSet(timezone: Int) extends UserEvent

  case class UserAgeRangeSet(min: Option[Int], max: Option[Int]) extends UserEvent

  case object NoOp extends UserEvent

}



case class FacebookAgeRange(min: Option[Int], max: Option[Int])
