package co.wishkeeper.server.user

import java.util.UUID

import co.wishkeeper.server.Error
import co.wishkeeper.server.WishStatus.WishStatus

sealed trait ValidationError extends Error

case class UserNotFound(email: String) extends ValidationError {
  override val message: String = s"A user with email $email was not found"
  override val code: String = "user.not-found"
}

case object NotFriends extends ValidationError {
  override val message: String = "Users are not friends"
  override val code: String = "user.not-friend"
}

case class InvalidStatusChange(toStatus: WishStatus, message: String) extends ValidationError {
  override val code: String = "user.status.invalid-change"
}

case class WishNotFound(wishId: UUID) extends ValidationError {
  override val message: String = s"Wish [$wishId] not found."
  override val code: String = "wish.not-found"
}

case object DummyError extends ValidationError {
  override val message: String = "Oops!"
  override val code: String = "dummy"
}

case class InvalidWishStatus(status: WishStatus) extends ValidationError {
  override val message: String = s"Cannot perform command when wish is in status $status"
  override val code: String = "wish.status.invalid"
}

case class GrantToSelfWhenReserved(wishId: UUID, reserver: UUID) extends ValidationError {
  override val message: String = s"Cannot grant wish [$wishId] to self when reserved by [$reserver]"
  override val code: String = "wish.self-grant.reserved"
}

case class GrantWhenNotReserved(wishId: UUID, granter: UUID, reservedBy: Option[UUID] = None) extends ValidationError {
  override val message: String = s"Cannot grant by user [$granter] when wish is ${reservedBy.fold("not reserved")(by => s"reserved by $by")}"
  override val code: String = "wish.grant.not-reserved"
}

case object NoChange extends ValidationError {
  override val message: String = "Sets a field to the same value"
  override val code: String = "no-change"
}

case object NoPictureToDelete extends ValidationError {
  override val message: String = "User profile picture does not exist"
  override val code: String = "user.profile.picture.not-exists"
}

case class AlreadyViewed(id: UUID) extends ValidationError {
  override val message: String = s"Notification [$id] already marked as viewed"
  override val code: String = "notification.already-viewed"
}

case class AlreadyFriend(friendId: UUID) extends ValidationError {
  override val message: String = s"User [$friendId] is already a friend"
  override val code: String = "user.already-friend"
}

case class Unauthorized(message: String = "Unauthorized to perform the requested action") extends ValidationError {
  override val code: String = "unauthorized"
}

case class EmailNotVerified(email: String) extends ValidationError {
  override val message: String = s"Email $email is not verified."
  override val code: String = "user.profile.email.not-verified"
}