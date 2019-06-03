package co.wishkeeper.server.user.events

import java.util.UUID

import co.wishkeeper.server.Events.{WishCreated, WishCurrencySet, WishDeleted, WishGranted, WishImageDeleted, WishImageSet, WishLinkSet, WishNameSet, WishOtherInfoSet, WishPriceSet, WishReserved, WishStoreSet, WishUnreserved}
import co.wishkeeper.server.{User, Wish, WishStatus}
import org.joda.time.DateTime

object WishlistEventHandlers {
  implicit val wishCreatedHandler: UserEventHandler[WishCreated] = (user: User, event: WishCreated, time: DateTime) =>
    updateWishProperty(user, event.wishId, _.withCreationTime(event.creationTime).withCreator(event.createdBy))
    .copy(lastWishlistChange = Option(time))

  implicit val wishDeletedHandler: UserEventHandler[WishDeleted] = (user: User, event: WishDeleted, time: DateTime) =>
    updateWishProperty(user, event.wishId, _.withStatus(WishStatus.Deleted, time))
    .copy(lastWishlistChange = Option(time))

  implicit val wishNameSetHandler: UserEventHandler[WishNameSet] = (user: User, event: WishNameSet, time: DateTime) =>
    updateWishProperty(user, event.wishId, _.withName(event.name))

  implicit val wishPriceSetHandler: UserEventHandler[WishPriceSet] = (user: User, event: WishPriceSet, time: DateTime) =>
    updateWishProperty(user, event.wishId, _.withPrice(event.price))

  implicit val wishLinkSetHandler: UserEventHandler[WishLinkSet] = (user: User, event: WishLinkSet, time: DateTime) =>
    updateWishProperty(user, event.wishId, _.withLink(event.link))

  implicit val wishCurrencySetHandler: UserEventHandler[WishCurrencySet] = (user: User, event: WishCurrencySet, time: DateTime) =>
    updateWishProperty(user, event.wishId, _.withCurrency(event.currency))

  implicit val wishStoreSetHandler: UserEventHandler[WishStoreSet] = (user: User, event: WishStoreSet, time: DateTime) =>
    updateWishProperty(user, event.wishId, _.withStore(event.store))

  implicit val wishOtherInfoSetHandler: UserEventHandler[WishOtherInfoSet] = (user: User, event: WishOtherInfoSet, time: DateTime) =>
    updateWishProperty(user, event.wishId, _.withOtherInfo(event.otherInfo))

  implicit val wishImageSetHandler: UserEventHandler[WishImageSet] = (user: User, event: WishImageSet, time: DateTime) =>
    updateWishProperty(user, event.wishId, _.withImage(event.imageLinks))

  implicit val wishImageDeletedHandler: UserEventHandler[WishImageDeleted] = (user: User, event: WishImageDeleted, time: DateTime) =>
    updateWishProperty(user, event.wishId, _.withoutImage)

  implicit val wishGrantedHandler: UserEventHandler[WishGranted] = (user: User, event: WishGranted, time: DateTime) => {
    val granter = user.wishes(event.wishId).status match {
      case WishStatus.Reserved(reserver) => Option(reserver)
      case _ => None
    }
    updateWishProperty(user, event.wishId, _.withStatus(WishStatus.Granted(granter), time))
  }

  implicit val wishReservedHandler: UserEventHandler[WishReserved] = (user: User, event: WishReserved, time: DateTime) =>
    updateWishProperty(user, event.wishId, _.withStatus(WishStatus.Reserved(event.reserverId), time)
      .withReserver(event.reserverId))

  implicit val wishUnreservedHandler: UserEventHandler[WishUnreserved] = (user: User, event: WishUnreserved, time: DateTime) =>
    updateWishProperty(user, event.wishId, _.withStatus(WishStatus.Active, time).withNoReserver)

  private def updateWishProperty(user: User, wishId: UUID, updater: Wish => Wish): User =
    user.copy(wishes = user.wishes + (wishId -> updater(user.wishes.getOrElse(wishId, Wish(wishId)))))
}
