package co.wishkeeper.test.utils

import co.wishkeeper.server.Wish
import org.joda.time.DateTime
import org.specs2.matcher.{AdaptableMatcher, MustThrownMatchers}

trait WishMatchers extends MustThrownMatchers {
  def beEqualToIgnoringDates: (Wish) => AdaptableMatcher[Wish] = ===(_:Wish) ^^^ ((_:Wish).copy(creationTime = new DateTime(0)))
}

object WishMatchers extends WishMatchers