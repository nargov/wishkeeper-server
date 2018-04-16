package co.wishkeeper.server.user.commands

import java.util.UUID.randomUUID

import co.wishkeeper.server.Error
import co.wishkeeper.server.UserTestHelper._
import co.wishkeeper.server.WishStatus.Granted
import co.wishkeeper.server.user.{InvalidStatusChange, WishNotFound}
import org.specs2.matcher.Matcher
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class GrantWishTest extends Specification {

  "GrantWish" should {
    "fail validation if self granting and status is not active" in new Context {
      val user = aUser.withDeletedWish(wishId)
      GrantWish.validator.validate(user, GrantWish(wishId)) must beLeft[Error](invalidStatusChange)
    }

    "return error if wish not found" in new Context {
      GrantWish.validator.validate(aUser, GrantWish(wishId)) must beLeft[Error](WishNotFound(wishId))
    }
  }

  trait Context extends Scope {
      val wishId = randomUUID()

  }

  def invalidStatusChange: Matcher[Error] = (err: Error) => err match {
    case InvalidStatusChange(Granted(None), _) => (true, s"Error is InvalidStatusChange to Granted by self")
    case _ => (false, "Error is not an InvalidStatusChange to Granted")
  }

}
