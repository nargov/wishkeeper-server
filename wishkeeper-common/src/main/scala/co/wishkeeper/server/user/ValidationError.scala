package co.wishkeeper.server.user

import java.util.UUID

import co.wishkeeper.server.Error
import co.wishkeeper.server.WishStatus.WishStatus

sealed trait ValidationError extends Error

case object NotFriends extends ValidationError {
  override val message: String = "Users are not friends"
}

case class InvalidStatusChange(toStatus: WishStatus, message: String) extends ValidationError

case class WishNotFound(wishId: UUID) extends ValidationError {
  override val message: String = s"Wish [$wishId] not found."
}

case object DummyError extends ValidationError{
  override val message: String = "Oops!"
}

case class InvalidWishStatus(status: WishStatus) extends ValidationError {
  override val message: String = s"Cannot perform command when wish is in status $status"
}

case class GrantToSelfWhenReserved(wishId: UUID, reserver: UUID) extends ValidationError {
  override val message: String = s"Cannot grant wish [$wishId] to self when reserved by [$reserver]"
}

case class GrantWhenNotReserved(wishId: UUID, granter: UUID, reservedBy: Option[UUID] = None) extends ValidationError {
  override val message: String = s"Cannot grant by user [$granter] when wish is ${reservedBy.fold("not reserved")(by => s"reserved by $by")}"
}

case object NoChange extends ValidationError{
  override val message: String = "Sets a field to the same value"
}

case object NoPictureToDelete extends ValidationError {
  override val message: String = "User profile picture does not exist"
}

case class AlreadyViewed(id: UUID) extends ValidationError {
  override val message: String = s"Notification [$id] already marked as viewed"
}