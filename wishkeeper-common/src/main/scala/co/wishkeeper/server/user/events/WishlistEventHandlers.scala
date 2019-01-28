package co.wishkeeper.server.user.events

import java.util.UUID

import co.wishkeeper.server.Events.{WishCreated, WishDeleted}
import co.wishkeeper.server.{User, Wish, WishStatus}
import org.joda.time.DateTime

object WishlistEventHandlers {
  implicit val wishCreatedHandler = new UserEventHandler[WishCreated] {
    override def apply(user: User, event: WishCreated, time: DateTime): User =
      updateWishProperty(user, event.wishId, _.withCreationTime(event.creationTime).withCreator(event.createdBy))
        .copy(lastWishlistChange = Option(time))
  }

  implicit val wishDeletedHandler = new UserEventHandler[WishDeleted] {
    override def apply(user: User, event: WishDeleted, time: DateTime): User =
      updateWishProperty(user, event.wishId, _.withStatus(WishStatus.Deleted, time))
        .copy(lastWishlistChange = Option(time))
  }

  private def updateWishProperty(user: User, wishId: UUID, updater: Wish => Wish): User =
    user.copy(wishes = user.wishes + (wishId -> updater(user.wishes.getOrElse(wishId, Wish(wishId)))))
}
