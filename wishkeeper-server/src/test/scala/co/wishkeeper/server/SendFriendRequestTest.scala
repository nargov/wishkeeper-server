package co.wishkeeper.server

import java.util.UUID
import java.util.UUID.randomUUID

import co.wishkeeper.server.Commands.SendFriendRequest
import co.wishkeeper.server.Events.{FriendRequestSent, UserEvent}
import org.specs2.matcher.Matcher
import org.specs2.mutable.Specification

class SendFriendRequestTest extends Specification {

  "should create FriendRequestSent event" >> {
    val friendId: UUID = randomUUID()
    val user: User = UserTestHelper.aUser
    SendFriendRequest(friendId).process(user) must contain(aFriendRequestSentEvent(friendId, user.id))
  }

  def aFriendRequestSentEvent(friendId: UUID, userId: UUID): Matcher[UserEvent] = (event: UserEvent) => event match {
    case FriendRequestSent(id, to, reqId) => (id == userId && to == friendId && reqId.isDefined,
      s"does not match friendId $friendId and userId $userId or does not have requestId defined")
    case _ => (false, "Not a FriendRequestSent event")
  }
}
