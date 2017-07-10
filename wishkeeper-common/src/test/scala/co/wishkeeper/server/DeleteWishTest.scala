package co.wishkeeper.server

import java.util.UUID.randomUUID

import co.wishkeeper.server.Commands.DeleteWish
import co.wishkeeper.server.Events.WishDeleted
import org.specs2.mutable.Specification

class DeleteWishTest extends Specification {
  "should create a WishDeleted event" in {
    val wishId = randomUUID()
    val user = User.createNew()
    DeleteWish(wishId).process(user) must contain(WishDeleted(wishId))
  }
}
