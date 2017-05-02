package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.Events._
import org.joda.time.DateTime

object Commands {

  sealed trait UserCommand {
    def process(user: User): List[UserEvent]
  }

  case class ConnectFacebookUser(facebookId: String, authToken: String, sessionId: UUID) extends UserCommand {
    override def process(user: User): List[UserEvent] = {
      val now = DateTime.now()
      val userConnectedEvent = UserConnected(user.id, now, sessionId)
      val facebookIdSetEvent = UserFacebookIdSet(user.id, facebookId)
      List(userConnectedEvent, facebookIdSetEvent)
    }
  }

  case class SetFacebookUserInfo(age_range: Option[AgeRange],
                                 birthday: Option[String],
                                 email: Option[String],
                                 first_name: Option[String],
                                 last_name: Option[String],
                                 name: Option[String],
                                 gender: Option[String],
                                 locale: Option[String],
                                 timezone: Option[Int]) extends UserCommand {

    override def process(user: User): List[UserEvent] = {
      val userId = user.id
      List(
        age_range.map(range => UserAgeRangeSet(userId, range.min, range.max)),
        birthday.flatMap(SetFacebookUserInfo.getValidUserBirthdayEvent(userId, _)),
        email.map(UserEmailSet(userId, _)),
        gender.map(UserGenderSet(userId, _)),
        locale.map(UserLocaleSet(userId, _)),
        timezone.map(UserTimeZoneSet(userId, _)),
        first_name.map(UserFirstNameSet(userId, _)),
        last_name.map(UserLastNameSet(userId, _)),
        name.map(UserNameSet(userId, _))).flatten
    }
  }

  object SetFacebookUserInfo {
    val getValidUserBirthdayEvent: (UUID, String) => Option[UserBirthdaySet] = (userId, day) => {
      if (day.matches("""^\d{2}/\d{2}(/\d{4})?$"""))
        Some(UserBirthdaySet(userId, day))
      else None
    }
  }

}
