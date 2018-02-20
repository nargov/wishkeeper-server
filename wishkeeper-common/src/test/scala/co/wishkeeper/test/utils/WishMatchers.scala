package co.wishkeeper.test.utils

import java.util.UUID

import co.wishkeeper.server.{Wish, WishStatus}
import org.joda.time.DateTime
import org.specs2.matcher.{AdaptableMatcher, Matcher, MustThrownMatchers}

trait WishMatchers extends MustThrownMatchers {
  def beEqualToIgnoringDates: (Wish) => AdaptableMatcher[Wish] = ===(_:Wish) ^^^ ((_:Wish).copy(creationTime = new DateTime(0)))

  def aGrantedWish(wishId: UUID, granter: UUID): Matcher[Wish] = (wish: Wish) =>
    (wish.id == wishId && wish.status == WishStatus.Granted(by = Option(granter)), s"Wish $wish doesn't match id $wishId and granter $granter")
}

object WishMatchers extends WishMatchers