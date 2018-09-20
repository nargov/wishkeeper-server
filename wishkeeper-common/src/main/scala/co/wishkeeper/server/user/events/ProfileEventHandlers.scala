package co.wishkeeper.server.user.events

import co.wishkeeper.server.Events.{UserGenderSet2, UserPictureDeleted}
import co.wishkeeper.server.{Events, GenderData, User}
import org.joda.time.DateTime

object ProfileEventHandlers {
  implicit val pictureDeleted = new UserEventHandler[UserPictureDeleted.type] {
    override def apply(user: User, event: Events.UserPictureDeleted.type, time: DateTime): User =
      user.copy(userProfile = user.userProfile.copy(picture = None))
  }

  implicit val userGenderSet = new UserEventHandler[UserGenderSet2] {
    override def apply(user: User, event: UserGenderSet2, time: DateTime): User =
      user.copy(userProfile = user.userProfile.copy(genderData = Option(GenderData(event.gender, event.customGender, event.genderPronoun))))
  }
}
