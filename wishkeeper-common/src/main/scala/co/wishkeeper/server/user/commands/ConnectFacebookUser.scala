package co.wishkeeper.server.user.commands

import java.util.UUID

import co.wishkeeper.server.Events._
import co.wishkeeper.server.{AgeRange, User}
import org.joda.time.DateTime

trait FacebookUserCommand extends UserCommand

case class ConnectFacebookUser(facebookId: String, authToken: String, sessionId: UUID, email: String) extends FacebookUserCommand {
  override def process(user: User): List[UserEvent] = {
    val now = DateTime.now()
    val userConnectedEvent = UserConnected(user.id, now, sessionId)
    val facebookIdSetEvent = UserFacebookIdSet(user.id, facebookId) //TODO only do this if facebook id was never set
    List(userConnectedEvent, facebookIdSetEvent)
  }
}

case class SetFacebookUserInfo(age_range: Option[AgeRange] = None,
                               birthday: Option[String] = None,
                               email: Option[String] = None,
                               first_name: Option[String] = None,
                               last_name: Option[String] = None,
                               name: Option[String] = None,
                               gender: Option[String] = None,
                               locale: Option[String] = None,
                               timezone: Option[Int] = None,
                               picture: Option[String] = None) extends FacebookUserCommand {

  override def process(user: User): List[UserEvent] = {
    val userId = user.id
    val profile = user.userProfile
    List(
      age_range.map(range => UserAgeRangeSet(userId, range.min, range.max)).filter(filterFor(profile.ageRange)),
      birthday.flatMap(SetFacebookUserInfo.getValidUserBirthdayEvent(userId, _)).filter(filterFor(profile.birthday)),
      email.map(UserEmailSet(userId, _)).filter(filterFor(profile.email)),
      gender.map(UserGenderSet(userId, _)).filter(filterFor(profile.gender)),
      locale.map(UserLocaleSet(userId, _)).filter(filterFor(profile.locale)),
      timezone.map(UserTimeZoneSet(userId, _)).filter(filterFor(profile.timezone)),
      first_name.map(UserFirstNameSet(userId, _)).filter(filterFor(profile.firstName)),
      last_name.map(UserLastNameSet(userId, _)).filter(filterFor(profile.lastName)),
      name.map(UserNameSet(userId, _)).filter(filterFor(profile.name)),
      picture.map(UserPictureSet(userId, _)).filter(filterFor(profile.picture))
    ).flatten
  }

  private val filterFor: Option[_] => Any => Boolean = opt => _ => opt.isEmpty
}

object SetFacebookUserInfo {
  val getValidUserBirthdayEvent: (UUID, String) => Option[UserBirthdaySet] = (userId, day) => {
    if (day.matches("""^\d{2}/\d{2}(/\d{4})?$"""))
      Some(UserBirthdaySet(userId, day))
    else None
  }
}
