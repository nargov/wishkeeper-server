package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.user.commands.DeleteWishImage
import co.wishkeeper.server.Events.WishImageDeleted
import org.specs2.mutable.Specification

class DeleteWishImageTest extends Specification {

  "creates a WishImageDeleted event" in {
    val wishId = UUID.randomUUID()
    val user = User.createNew()
    val userEvents: Seq[Events.UserEvent] = DeleteWishImage(wishId).process(user)
    userEvents must contain(WishImageDeleted(wishId))
  }
}
