package co.wishkeeper.server.user.events

import co.wishkeeper.server.Events.UserPictureDeleted
import co.wishkeeper.server.{Events, User}
import org.joda.time.DateTime

object ProfileEventHandlers {
  implicit val pictureDeleted = new UserEventHandler[UserPictureDeleted.type] {
    override def apply(user: User, event: Events.UserPictureDeleted.type, time: DateTime): User =
      user.copy(userProfile = user.userProfile.copy(picture = None))
  }
}
