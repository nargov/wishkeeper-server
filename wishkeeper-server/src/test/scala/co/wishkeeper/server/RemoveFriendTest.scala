package co.wishkeeper.server

import java.util.UUID
import java.util.UUID.randomUUID

import co.wishkeeper.server.user.commands.RemoveFriend
import co.wishkeeper.server.Events.FriendRemoved
import org.specs2.mutable.Specification

class RemoveFriendTest extends Specification {

  "should return a FriendRemoved event" >> {
    val user: User = UserTestHelper.aUser
    val friendId: UUID = randomUUID()
    RemoveFriend(friendId).process(user) must beEqualTo(List(FriendRemoved(user.id, friendId)))
  }
}
