package co.wishkeeper.server.user.commands

import java.util.UUID

import co.wishkeeper.server.Events._
import co.wishkeeper.server.WishStatus.{Active, Reserved}
import co.wishkeeper.server.user.{InvalidStatusChange, ValidationError, WishNotFound}
import co.wishkeeper.server.{User, Wish}
import org.joda.time.DateTime

case class SetWishDetails(wish: Wish) extends UserCommand {
  override def process(user: User): List[UserEvent] =
    creationEventIfNotExists(user) ++ List(
      wish.name.map(WishNameSet(wish.id, _)),
      wish.link.map(WishLinkSet(wish.id, _)),
      wish.store.map(WishStoreSet(wish.id, _)),
      wish.otherInfo.map(WishOtherInfoSet(wish.id, _)),
      wish.price.map(WishPriceSet(wish.id, _)),
      wish.currency.map(WishCurrencySet(wish.id, _)),
      wish.image.map(WishImageSet(wish.id, _))
    ).flatten

  private def creationEventIfNotExists(user: User): List[WishCreated] = {
    if (!user.wishes.contains(wish.id))
      List(WishCreated(wish.id, user.id, DateTime.now()))
    else
      Nil
  }
}

case class DeleteWishImage(wishId: UUID) extends UserCommand {
  override def process(user: User): List[UserEvent] = List(WishImageDeleted(wishId))
}

case class DeleteWish(wishId: UUID) extends UserCommand {
  override def process(user: User): List[UserEvent] = WishDeleted(wishId) :: Nil
}

case class GrantWish(wishId: UUID) extends UserCommand {
  override def process(user: User): List[UserEvent] = WishGranted(wishId) :: Nil
}

case class ReserveWish(reserverId: UUID, wishId: UUID) extends UserCommand {
  override def process(user: User): List[UserEvent] = List(
    WishReserved(wishId, reserverId),
    WishReservedNotificationCreated(UUID.randomUUID(), wishId, reserverId))
}
object ReserveWish {
  implicit val validator = new UserCommandValidator[ReserveWish] {
    override def validate(user: User, command: ReserveWish): Either[ValidationError, Unit] = {
      user.wishes.get(command.wishId).map(_.status match {
        case Active => Right()
        case s => Left(InvalidStatusChange(Reserved(command.reserverId), s"Cannot reserve wish in status $s"))
      }).getOrElse(Left(WishNotFound(command.wishId)))
    }
  }
}

case class UnreserveWish(wishId: UUID) extends UserCommand {
  override def process(user: User): List[UserEvent] = List(
    WishUnreserved(wishId),
    WishUnreservedNotificationCreated(UUID.randomUUID(), wishId))
}
object UnreserveWish {
  implicit val validator = new UserCommandValidator[UnreserveWish] {
    override def validate(user: User, command: UnreserveWish): Either[ValidationError, Unit] =
      user.wishes.get(command.wishId).map(_.status match {
        case Reserved(_) => Right(())
        case s => Left(InvalidStatusChange(s, s"Can only Unreserve a reserved wish. Current status of wish [${command.wishId}] is $s"))
      }).getOrElse(Left(WishNotFound(command.wishId)))
  }
}