package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.user.commands.SetWishDetails
import co.wishkeeper.server.Events._
import co.wishkeeper.server.UserTestHelper._
import org.joda.time.DateTime
import org.specs2.matcher.Matcher
import org.specs2.mutable.Specification

class SetWishDetailsTest extends Specification {

  val user = aUser
  val wishId = UUID.randomUUID()

  "should create event for name" in {
    val name = "name"
    SetWishDetails(Wish(wishId).withName(name)).process(user) must contain(WishNameSet(wishId, name))
  }

  "should create event for link" in {
    val link = "link"
    SetWishDetails(Wish(wishId).withLink(link)).process(user) must contain(WishLinkSet(wishId, link))
  }

  "should create event for store" in {
    val store = "store"
    SetWishDetails(Wish(wishId).withStore(store)).process(user) must contain(WishStoreSet(wishId, store))
  }

  "should create event for other info" in {
    val otherInfo = "other info"
    SetWishDetails(Wish(wishId).withOtherInfo(otherInfo)).process(user) must contain(WishOtherInfoSet(wishId, otherInfo))
  }

  "should create events for multiple properties" in {
    val name = "name"
    val link = "link"
    val store = "store"
    val otherInfo = "other info"

    val command = SetWishDetails(
      Wish(wishId).
        withName(name).
        withLink(link).
        withStore(store).
        withOtherInfo(otherInfo))

    val expectedEvents: List[UserEvent] = List(
      WishNameSet(wishId, name),
      WishLinkSet(wishId, link),
      WishStoreSet(wishId, store),
      WishOtherInfoSet(wishId, otherInfo)
    )

    command.process(user) must contain(allOf(expectedEvents: _*))
  }

  "should create event for price" in {
    val price = "5.00"
    SetWishDetails(Wish(wishId).withPrice(price)).process(user) must contain(WishPriceSet(wishId, price))
  }

  "should create event for currency" in {
    val currency = "USD"
    SetWishDetails(Wish(wishId).withCurrency(currency)).process(user) must contain(WishCurrencySet(wishId, currency))
  }

  "should create event for image" in {
    val imageLink = ImageLink("http://www.myimage.com", 10, 20, "image/jpeg")
    val imageLinks = ImageLinks(imageLink :: Nil)
    SetWishDetails(Wish(wishId).withImage(imageLinks)).process(user) must contain(WishImageSet(wishId, imageLinks))
  }

  "should create a wish creation event if the wish is new" in {
    SetWishDetails(Wish(wishId)).process(user) must containWishCreatedEvent(wishId, user.id)
  }

  "should not create a wish creation event if the wish already exists" in {
    SetWishDetails(Wish(wishId)).process(user.withWish(wishId)) must beEmpty
  }

  def containWishCreatedEvent(wishId: UUID, createdBy: UUID): Matcher[List[UserEvent]] = { eventList: List[UserEvent] =>
    (eventList.exists(_ match {
      case WishCreated(wish, userId, _) if wish == wishId && userId == createdBy => true
      case _ => false
    }), s"$eventList does not contain WishCreated($wishId, $createdBy, any[DateTime])")
  }
}
