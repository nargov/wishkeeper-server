package co.wishkeeper.server

import java.util.UUID
import java.util.UUID.randomUUID

import co.wishkeeper.server.user.commands.SendFriendRequest
import co.wishkeeper.server.Events.{FriendRequestSent, UserEvent}
import org.specs2.matcher.Matcher
import org.specs2.mutable.Specification
import UserTestHelper._
import org.specs2.specification.Scope

class SendFriendRequestTest extends Specification {

  "should create FriendRequestSent event" in new Context {
    val user = aUser
    SendFriendRequest(friendId).process(user) must contain(aFriendRequestSentEvent(friendId, user.id))
  }

  "should not create an event if friend request for this friend already exists" in new Context {
    val user = aUser.withSentFriendRequest(randomUUID(), friendId)
    SendFriendRequest(friendId).process(user) must beEmpty
  }

  trait Context extends Scope {
    val friendId: UUID = randomUUID()
  }

  def aFriendRequestSentEvent(friendId: UUID, userId: UUID): Matcher[UserEvent] = (event: UserEvent) => event match {
    case FriendRequestSent(id, to, reqId) => (id == userId && to == friendId && reqId.isDefined,
      s"does not match friendId $friendId and userId $userId or does not have requestId defined")
    case _ => (false, "Not a FriendRequestSent event")
  }
}
