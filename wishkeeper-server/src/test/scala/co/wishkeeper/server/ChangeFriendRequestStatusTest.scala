package co.wishkeeper.server

import java.util.UUID
import java.util.UUID.randomUUID

import co.wishkeeper.server.Commands.ChangeFriendRequestStatus
import co.wishkeeper.server.Events.{FriendRequestAcceptedNotificationCreated, FriendRequestStatusChanged, UserEvent}
import co.wishkeeper.server.FriendRequestStatus.Approved
import co.wishkeeper.server.UserTestHelper._
import org.specs2.matcher.Matcher
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class ChangeFriendRequestStatusTest extends Specification {

  trait Context extends Scope {
    val friend = randomUUID()
    val notificationId = randomUUID()
    val requestId = randomUUID()
    val user = aUser.withExistingFriendRequest(requestId, friend).withFriendRequestNotification(notificationId, requestId, friend)
  }

  "should return a FriendRequestStatusChanged event" in new Context {
    val friendRequestStatusChanged: UserEvent = FriendRequestStatusChanged(user.id, requestId, friend, Approved)
    ChangeFriendRequestStatus(requestId, Approved).process(user) must contain(friendRequestStatusChanged)
  }


  //noinspection MatchToPartialFunction
  def aFriendRequestAcceptedNotificationCreated(userId:UUID, friendId: UUID, requestId: UUID): Matcher[UserEvent] = (e: UserEvent) => e match {
    case event: FriendRequestAcceptedNotificationCreated =>
      (event.userId == userId && event.by == friendId && event.requestId == requestId,
        s"$event doesn't match userId $userId, friendId $friendId, requestId $requestId")
    case _ => (false, "Not a FriendRequestAcceptedNotificationCreated event")
  }
}
