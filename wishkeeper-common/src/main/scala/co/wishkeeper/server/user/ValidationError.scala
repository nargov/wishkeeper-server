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