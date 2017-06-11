package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.Commands.CreateNewWish
import co.wishkeeper.server.Events.WishCreated
import org.specs2.mutable.Specification

class CreateNewWishTest extends Specification {
  "creates a WishCreated event" in {
    val user = User.createNew()
    val wishId = UUID.randomUUID()
    CreateNewWish(wishId).process(user) must beEqualTo(List(WishCreated(wishId, user.id)))
  }
}
