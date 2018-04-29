package co.wishkeeper.server.user.commands

import java.util.UUID
import java.util.UUID.randomUUID

import co.wishkeeper.server.Error
import co.wishkeeper.server.UserTestHelper._
import co.wishkeeper.server.WishStatus.Granted
import co.wishkeeper.server.user.{GrantToSelfWhenReserved, GrantWhenNotReserved, InvalidStatusChange, WishNotFound}
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

    "return error if granter is specified but wish is not reserved" in new Context {
      GrantWish.validator.validate(aUser.withWish(wishId), GrantWish(wishId, Option(granter))) must
        beLeft[Error](GrantWhenNotReserved(wishId, granter))
    }

    "return error if granter is specified but wish is reserved by someone else" in new Context {
      GrantWish.validator.validate(aUser.withReservedWish(wishId, reserver), GrantWish(wishId, Option(granter))) must
        beLeft[Error](GrantWhenNotReserved(wishId, granter, Some(reserver)))
    }

    "return error if granter is not specified but wish was reserved" in new Context {
      GrantWish.validator.validate(aUser.withReservedWish(wishId, reserver), GrantWish(wishId, None)) must
        beLeft[Error](GrantToSelfWhenReserved(wishId, reserver))
    }
  }

  trait Context extends Scope {
      val reserver: UUID = randomUUID()
      val granter: UUID = randomUUID()
      val wishId = randomUUID()
  }

  def invalidStatusChange: Matcher[Error] = (err: Error) => err match {
    case InvalidStatusChange(Granted(None), _) => (true, s"Error is InvalidStatusChange to Granted by self")
    case _ => (false, "Error is not an InvalidStatusChange to Granted")
  }

}
