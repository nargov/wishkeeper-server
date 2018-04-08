package co.wishkeeper.server.user.commands

import java.util.UUID

import co.wishkeeper.server.Events._
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

case object MarkAllNotificationsViewed extends UserCommand {
  override def process(user: User): List[UserEvent] =
    user.notifications.filterNot(_.viewed).map(notification => NotificationViewed(notification.id))
}

case class RemoveFriend(friendId: UUID) extends UserCommand {
  override def process(user: User): List[UserEvent] = FriendRemoved(user.id, friendId) :: Nil
}