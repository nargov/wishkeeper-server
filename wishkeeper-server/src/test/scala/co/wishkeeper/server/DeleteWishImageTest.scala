package co.wishkeeper.server

import java.util.UUID
import java.util.UUID.randomUUID

import co.wishkeeper.server.Events.WishImageDeleted
import co.wishkeeper.server.UserTestHelper._
import co.wishkeeper.server.WishStatus.Reserved
import co.wishkeeper.server.user.commands.DeleteWishImage
import co.wishkeeper.server.user.{InvalidWishStatus, ValidationError, WishNotFound}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class DeleteWishImageTest extends Specification {

  "creates a WishImageDeleted event" in new Context {
    val userEvents: Seq[Events.UserEvent] = DeleteWishImage(wishId).process(user)
    userEvents must contain(WishImageDeleted(wishId))
  }

  "returns a validation error if wish is not in Active state" in new Context {
    val reserver: UUID = randomUUID()
    DeleteWishImage.validator.validate(user.withReservedWish(wishId, reserver), DeleteWishImage(wishId)) must
      beLeft[ValidationError](InvalidWishStatus(Reserved(reserver)))
  }

  "returns a validation error if wish is not found" in new Context {
    DeleteWishImage.validator.validate(user, DeleteWishImage(wishId)) must beLeft[ValidationError](WishNotFound(wishId))
  }

  trait Context extends Scope {
    val wishId = randomUUID()
    val user = aUser
  }
}
