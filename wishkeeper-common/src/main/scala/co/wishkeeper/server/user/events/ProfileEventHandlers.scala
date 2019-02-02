package co.wishkeeper.server.user.events

import co.wishkeeper.server.Events.{UserAnniversarySet, UserGenderSet, UserGenderSet2, UserPictureDeleted}
import co.wishkeeper.server.user.Gender
import co.wishkeeper.server.{Events, GenderData, User}
import org.joda.time.DateTime

object ProfileEventHandlers {
  implicit val pictureDeleted = new UserEventHandler[UserPictureDeleted.type] {
    override def apply(user: User, event: Events.UserPictureDeleted.type, time: DateTime): User =
      user.copy(userProfile = user.userProfile.copy(picture = None))
  }

  implicit val userGenderSet2 = new UserEventHandler[UserGenderSet2] {
    override def apply(user: User, event: UserGenderSet2, time: DateTime): User =
      user.copy(userProfile = user.userProfile.copy(genderData = Option(GenderData(event.gender, event.customGender, event.genderPronoun))))
  }

  implicit val userGenderSet = new UserEventHandler[UserGenderSet] {
    override def apply(user: User, event: UserGenderSet, time: DateTime): User =
      user.copy(userProfile = user.userProfile.copy(
        gender = Option(event.gender),
        genderData = Option(GenderData(Gender.fromString(event.gender)))))
  }

  implicit val userAnniversarySet = new UserEventHandler[UserAnniversarySet] {
    override def apply(user: User, event: UserAnniversarySet, time: DateTime): User =
      user.copy(userProfile = user.userProfile.copy(anniversary = Option(event.date)))
  }
}
