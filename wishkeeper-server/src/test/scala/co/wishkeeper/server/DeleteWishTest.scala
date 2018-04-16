package co.wishkeeper.server

import java.util.UUID.randomUUID

import co.wishkeeper.server.Events.{UserEvent, WishDeleted}
import co.wishkeeper.server.UserTestHelper._
import co.wishkeeper.server.WishStatus.Deleted
import co.wishkeeper.server.user.{InvalidStatusChange, WishNotFound}
import co.wishkeeper.server.user.commands.DeleteWish
import org.specs2.matcher.Matcher
import org.specs2.mutable.Specification

class DeleteWishTest extends Specification {
  "should create a WishDeleted event" in {
    val wishId = randomUUID()
    val user = User.createNew()
    DeleteWish(wishId).process(user) must contain[UserEvent](WishDeleted(wishId))
  }

  "should pass validation if wish is Active" in {
    val wishId = randomUUID()
    DeleteWish.validator.validate(aUser.withWish(wishId), DeleteWish(wishId)) must beRight
  }

  "should fail validation if wish is not Active" in {
    val wishId = randomUUID()
    DeleteWish.validator.validate(aUser.withReservedWish(wishId, randomUUID()), DeleteWish(wishId)) must beLeft[Error](invalidStatusChangeToDeleted)
  }

  "should fail validation if wish is not found" in {
    val missingWishId = randomUUID()
    DeleteWish.validator.validate(aUser, DeleteWish(missingWishId)) must beLeft[Error](WishNotFound(missingWishId))
  }


  def invalidStatusChangeToDeleted: Matcher[Error] = (err: Error) => err match {
    case InvalidStatusChange(Deleted, _) => (true, "Error is InvalidStatusChange to Deleted")
    case _ => (false, "Error is not InvalidStatusChange to Deleted")
  }
}
