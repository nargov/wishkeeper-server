package co.wishkeeper.server.user.commands

import java.util.UUID.randomUUID

import co.wishkeeper.server.UserTestHelper.{aUser, _}
import co.wishkeeper.server.WishStatus.Active
import co.wishkeeper.server.user.{InvalidStatusChange, ValidationError, WishNotFound}
import org.specs2.matcher.Matcher
import org.specs2.mutable.Specification

class UnreserveWishTest extends Specification {
  "UnreservedWish" should {
    "fail validation if wish is not in reserved state" in {
      val wishId = randomUUID()
      val user = aUser.withWish(wishId)
      UnreserveWish.validator.validate(user, UnreserveWish(wishId)) must beLeft(anInvalidStatusChangeValidationError)
    }

    "fail validation if wish doesn't exist" in {
      val wishId = randomUUID()
      UnreserveWish.validator.validate(aUser, UnreserveWish(wishId)) must beLeft[ValidationError](WishNotFound(wishId))
    }
  }

  def anInvalidStatusChangeValidationError: Matcher[ValidationError] = (err: ValidationError) => err match {
    case InvalidStatusChange(Active, _) => (true, "$err is an InvalidStatusChange from Active to Reserved")
    case _ => (false, "$err is not an InvalidStatusChange from Active to Reserved")
  }
}
