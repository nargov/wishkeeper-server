package co.wishkeeper.server

import java.util.UUID.randomUUID

import co.wishkeeper.server.Commands.ChangeFriendRequestStatus
import co.wishkeeper.server.Events.{FriendRequestStatusChanged, UserEvent}
import co.wishkeeper.server.FriendRequestStatus.Approved
import co.wishkeeper.server.UserTestHelper._
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class ChangeFriendRequestStatusTest extends Specification {

  trait Context extends Scope {
    val sender = randomUUID()
    val notificationId = randomUUID()
    val requestId = randomUUID()
    val user = aUser.withExistingFriendRequest(requestId, sender).withFriendRequestNotification(notificationId, requestId, sender)
  }

  "should return a FriendRequestStatusChanged event" in new Context {
    val friendRequestStatusChanged: UserEvent = FriendRequestStatusChanged(user.id, requestId, sender, Approved)
    ChangeFriendRequestStatus(requestId, Approved).process(user) must contain(friendRequestStatusChanged)
  }
}
