package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.Commands.SetWishDetails
import co.wishkeeper.server.Events._
import org.specs2.mutable.Specification

class SetWishDetailsTest extends Specification {

  val user = User(UUID.randomUUID())
  val wishId = UUID.randomUUID()

  "should create event for name" in {
    val name = "name"
    SetWishDetails(Wish(wishId).withName(name)).process(user) must beEqualTo(List(WishNameSet(wishId, name)))
  }

  "should create event for link" in {
    val link = "link"
    SetWishDetails(Wish(wishId).withLink(link)).process(user) must beEqualTo(List(WishLinkSet(wishId, link)))
  }

  "should create event for image link" in {
    val link = "link"
    SetWishDetails(Wish(wishId).withImageLink(link)).process(user) must beEqualTo(List(WishImageLinkSet(wishId, link)))
  }

  "should create event for store" in {
    val store = "store"
    SetWishDetails(Wish(wishId).withStore(store)).process(user) must beEqualTo(List(WishStoreSet(wishId, store)))
  }

  "should create event for other info" in {
    val otherInfo = "other info"
    SetWishDetails(Wish(wishId).withOtherInfo(otherInfo)).process(user) must beEqualTo(List(WishOtherInfoSet(wishId, otherInfo)))
  }

  "should create events for multiple properties" in {
    val name = "name"
    val link = "link"
    val imageLink = "image link"
    val store = "store"
    val otherInfo = "other info"

    val command = SetWishDetails(
      Wish(wishId).
        withName(name).
        withLink(link).
        withImageLink(imageLink).
        withStore(store).
        withOtherInfo(otherInfo))

    val expectedEvents: List[UserEvent] = List(
      WishNameSet(wishId, name),
      WishLinkSet(wishId, link),
      WishImageLinkSet(wishId, imageLink),
      WishStoreSet(wishId, store),
      WishOtherInfoSet(wishId, otherInfo)
    )

    command.process(user) must contain(allOf(expectedEvents: _*))
  }
}
