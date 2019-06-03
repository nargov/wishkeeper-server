package co.wishkeeper.server.user.events

import co.wishkeeper.server.Events.{UserAgeRangeSet, UserAnniversarySet, UserBirthdaySet, UserEmailSet, UserFacebookIdSet, UserFirstNameSet, UserGenderSet, UserGenderSet2, UserLastNameSet, UserLocaleSet, UserNameSet, UserPictureDeleted, UserPictureSet, UserTimeZoneSet}
import co.wishkeeper.server.user.Gender
import co.wishkeeper.server.{AgeRange, Events, GenderData, SocialData, User}
import org.joda.time.DateTime

object ProfileEventHandlers {
  implicit val pictureDeleted: UserEventHandler[Events.UserPictureDeleted.type] =
    (user: User, event: Events.UserPictureDeleted.type, time: DateTime) =>
      user.copy(userProfile = user.userProfile.copy(picture = None))

  implicit val userGenderSet2: UserEventHandler[UserGenderSet2] = (user: User, event: UserGenderSet2, time: DateTime) =>
    user.copy(userProfile = user.userProfile.copy(genderData =
      Option(GenderData(event.gender, event.customGender, event.genderPronoun))))

  implicit val userGenderSet: UserEventHandler[UserGenderSet] = (user: User, event: UserGenderSet, time: DateTime) =>
    user.copy(userProfile = user.userProfile.copy(
      gender = Option(event.gender),
      genderData = Option(GenderData(Gender.fromString(event.gender)))))

  implicit val userAnniversarySet: UserEventHandler[UserAnniversarySet] = (user: User, event: UserAnniversarySet, time: DateTime) =>
    user.copy(userProfile = user.userProfile.copy(anniversary = Option(event.date)))

  implicit val userFirstNameSet: UserEventHandler[UserFirstNameSet] = (user: User, event: UserFirstNameSet, time: DateTime) =>
    user.copy(userProfile = user.userProfile.copy(firstName = Option(event.name)))

  implicit val userLastNameSet: UserEventHandler[UserLastNameSet] = (user: User, event: UserLastNameSet, time: DateTime) =>
    user.copy(userProfile = user.userProfile.copy(lastName = Option(event.name)))

  implicit val userNameSet: UserEventHandler[UserNameSet] = (user: User, event: UserNameSet, time: DateTime) =>
    user.copy(userProfile = user.userProfile.copy(name = Option(event.name)))

  implicit val userBirthdaySet: UserEventHandler[UserBirthdaySet] = (user: User, event: UserBirthdaySet, time: DateTime) =>
    user.copy(userProfile = user.userProfile.copy(birthday = Option(event.birthday)))

  implicit val userEmailSet: UserEventHandler[UserEmailSet] = (user: User, event: UserEmailSet, time: DateTime) =>
    user.copy(userProfile = user.userProfile.copy(email = Option(event.email)))

  implicit val userLocaleSet: UserEventHandler[UserLocaleSet] = (user: User, event: UserLocaleSet, time: DateTime) =>
    user.copy(userProfile = user.userProfile.copy(locale = Option(event.locale)))

  implicit val userTimeZoneSet: UserEventHandler[UserTimeZoneSet] = (user: User, event: UserTimeZoneSet, time: DateTime) =>
    user.copy(userProfile = user.userProfile.copy(timezone = Option(event.timezone)))

  implicit val userAgeRangeSet: UserEventHandler[UserAgeRangeSet] = (user: User, event: UserAgeRangeSet, time: DateTime) =>
    user.copy(userProfile = user.userProfile.copy(ageRange = Option(AgeRange(event.min, event.max))))

  implicit val userPictureSet: UserEventHandler[UserPictureSet] = (user: User, event: UserPictureSet, time: DateTime) =>
    user.copy(userProfile = user.userProfile.copy(picture = Option(event.pictureLink)))

  implicit val userFacebookIdSet: UserEventHandler[UserFacebookIdSet] = (user: User, event: UserFacebookIdSet, time: DateTime) =>
    user.copy(userProfile = user.userProfile.copy(socialData =
      user.userProfile.socialData match {
        case Some(data) => Option(data.copy(facebookId = Option(event.facebookId)))
        case None => Option(SocialData(Option(event.facebookId)))
      }))

}
