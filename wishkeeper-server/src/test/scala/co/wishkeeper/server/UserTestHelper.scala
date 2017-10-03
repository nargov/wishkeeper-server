package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.Events._
import co.wishkeeper.server.EventsTestHelper.asEventInstant
import co.wishkeeper.server.FriendRequestStatus.Approved
import org.joda.time.DateTime

object UserTestHelper {
  def aUser: User = {
    val id = UUID.randomUUID()
    User(id).applyEvent(UserEventInstant(UserConnected(id, DateTime.now().minusDays(1), UUID.randomUUID()), DateTime.now().minusDays(1)))
  }

  def now = DateTime.now()

  implicit class TestUserOps(user: User) {
    def withWish(id: UUID = UUID.randomUUID()): User = {
      user.applyEvent(UserEventInstant(WishCreated(id, user.id, now), now))
    }

    def withExistingFriendRequest(reqId: UUID, from: UUID): User = {
      user.applyEvent(UserEventInstant(FriendRequestReceived(user.id, from, reqId), now))
    }

    def withFriendRequestNotification(notificationId: UUID, reqId: UUID, from: UUID): User = {
      user.applyEvent(UserEventInstant(FriendRequestNotificationCreated(notificationId, user.id, from, reqId), now))
    }

    def withFriend(friendId: UUID = UUID.randomUUID(), reqId: UUID = UUID.randomUUID()): User = {
      user.
        withExistingFriendRequest(reqId, friendId).
        withFriendRequestNotification(UUID.randomUUID(), reqId, friendId).
        applyEvent(asEventInstant(FriendRequestStatusChanged(user.id, reqId, friendId, Approved)))
    }

    def withSentFriendRequest(reqId: UUID, friend: UUID): User = {
      user.
        applyEvent(asEventInstant(FriendRequestSent(user.id, friend, reqId)))
    }

    def withSentFriendRequestAccepted(reqId: UUID, friend: UUID): User = {
      user.withSentFriendRequest(reqId, friend).
        applyEvent(asEventInstant(FriendRequestStatusChanged(user.id, reqId, user.id, Approved)))
    }

  }

}
