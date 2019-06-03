package co.wishkeeper.server.user.events

import co.wishkeeper.server.Events.{FriendRemoved, FriendRequestReceived, FriendRequestSent}
import co.wishkeeper.server.{FriendRequest, User}
import org.joda.time.DateTime

object FriendsEventHandlers {
  implicit val friendRequestSentHandler: UserEventHandler[FriendRequestSent] = (user: User, event: FriendRequestSent, time: DateTime) =>
    event.id.map(requestId => user.copy(friends = user.friends.copy(sentRequests =
      user.friends.sentRequests :+ FriendRequest(requestId, event.to, event.userId)))).getOrElse(user)

  implicit val friendRequestReceivedHandler: UserEventHandler[FriendRequestReceived] =
    (user: User, event: FriendRequestReceived, time: DateTime) =>
      event.id.map(requestId => user.copy(friends = user.friends.copy(receivedRequests =
        user.friends.receivedRequests :+ FriendRequest(requestId, event.userId, event.from)))).getOrElse(user)

  implicit val friendRemovedHandler: UserEventHandler[FriendRemoved] = (user: User, event: FriendRemoved, time: DateTime) =>
    user.copy(friends = user.friends.copy(current = user.friends.current.filterNot(_ == event.friendId)))
}
