package co.wishkeeper.server.user.commands

import java.util.UUID

import co.wishkeeper.server.Events._
import co.wishkeeper.server.user.{AlreadyViewed, NoChange, NoPictureToDelete, ValidationError}
import co.wishkeeper.server.{FriendRequestStatus, User}

trait UserCommand {
  def process(user: User): List[UserEvent]
}

case class SendFriendRequest(friendId: UUID) extends UserCommand {
  override def process(user: User): List[UserEvent] =
    if (user.friends.sentRequests.exists(_.userId == friendId))
      Nil
    else
      List(FriendRequestSent(user.id, friendId, Option(UUID.randomUUID())))
}
object SendFriendRequest {
  implicit val validator = new UserCommandValidator[SendFriendRequest] {
    override def validate(user: User, command: SendFriendRequest): Either[ValidationError, Unit] = Right(())
  }
}


case class SetFlagFacebookFriendsListSeen(seen: Boolean = true) extends UserCommand {
  override def process(user: User): List[UserEvent] = FacebookFriendsListSeen(seen) :: Nil
}

case class ChangeFriendRequestStatus(requestId: UUID, status: FriendRequestStatus) extends UserCommand {
  override def process(user: User): List[UserEvent] = {
    user.friends.receivedRequests.
      find(_.id == requestId).
      map(request => FriendRequestStatusChanged(user.id, requestId, request.from, status) :: Nil).
      getOrElse(Nil)
  }
}
object ChangeFriendRequestStatus {
  implicit val validator: UserCommandValidator[ChangeFriendRequestStatus] = UserCommandValidator.Always
}

case object MarkAllNotificationsViewed extends UserCommand {
  override def process(user: User): List[UserEvent] =
    user.notifications.filterNot(_.viewed).map(notification => NotificationViewed(notification.id))
}

case class MarkNotificationViewed(id: UUID) extends UserCommand {
  override def process(user: User): List[UserEvent] = NotificationViewed(id) :: Nil
}
object MarkNotificationViewed {
  implicit val validator: UserCommandValidator[MarkNotificationViewed] = (user, event) => Either.cond(
    user.notifications.exists(n => n.id == event.id && !n.viewed), (), AlreadyViewed(event.id))
}

case class RemoveFriend(friendId: UUID) extends UserCommand {
  override def process(user: User): List[UserEvent] = FriendRemoved(user.id, friendId) :: Nil
}

case class SetDeviceNotificationId(id: String) extends UserCommand {
  override def process(user: User): List[UserEvent] = DeviceNotificationIdSet(id) :: Nil
}
object SetDeviceNotificationId {
  implicit val validator = new UserCommandValidator[SetDeviceNotificationId] {
    override def validate(user: User, command: SetDeviceNotificationId): Either[ValidationError, Unit] =
      Either.cond(user.settings.deviceNotificationId != Option(command.id), (), NoChange)
  }
}

case object DeleteUserPicture extends UserCommand {
  override def process(user: User): List[UserEvent] = UserPictureDeleted :: Nil

  implicit val validator: UserCommandValidator[DeleteUserPicture.type] =
    (user: User, _: DeleteUserPicture.type) => Either.cond(user.userProfile.picture.isDefined, (), NoPictureToDelete)
}