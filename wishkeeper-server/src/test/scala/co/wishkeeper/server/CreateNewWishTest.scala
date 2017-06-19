package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.Commands.CreateNewWish
import co.wishkeeper.server.Events.WishCreated
import org.joda.time.DateTime
import org.specs2.mutable.Specification

class CreateNewWishTest extends Specification {
  "creates a WishCreated event" in {
    val user = User.createNew()
    val wishId = UUID.randomUUID()
    val creationTime = DateTime.now()
    CreateNewWish(wishId, creationTime).process(user) must beEqualTo(List(WishCreated(wishId, user.id, creationTime)))
  }
}
