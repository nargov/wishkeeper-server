package co.wishkeeper.server.user.commands

import java.util.UUID
import java.util.UUID.randomUUID

import co.wishkeeper.server.UserTestHelper._
import co.wishkeeper.server.WishStatus.Reserved
import co.wishkeeper.server.user.{InvalidStatusChange, ValidationError}
import org.specs2.matcher.Matcher
import org.specs2.mutable.Specification

class ReserveWishTest extends Specification {
  "ReserveWish" should {
    "fail validation when wish is not active" in {
      val wishId = randomUUID()
      val reserver = randomUUID()
      ReserveWish.validator.validate(aUser.withReservedWish(wishId, reserver), ReserveWish(reserver, wishId)) must beLeft[ValidationError](
        invalidReserveStatus(reserver)
      )
    }
  }

  def invalidReserveStatus(reserver: UUID): Matcher[ValidationError] = (err: ValidationError) => err match {
    case InvalidStatusChange(Reserved(r), _) => (r == reserver, s"Error is InvalidStatusChange to Reserved by $r")
    case _ => (false, "Error is not an InvalidStatusChange to Reserved")
  }
}
