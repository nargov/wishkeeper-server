package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.Events._
import co.wishkeeper.server.EventsTestHelper.{EventsList, asEventInstant}
import co.wishkeeper.server.FriendRequestStatus.Approved
import org.joda.time.{DateTime, LocalDate}

object UserTestHelper {
  def aUser: User = {
    val id = UUID.randomUUID()
    User(id).applyEvent(UserEventInstant(UserConnected(id, DateTime.now().minusDays(1), UUID.randomUUID()), DateTime.now().minusDays(1)))
  }

  def now = DateTime.now()

  implicit class TestUserOps(user: User) {
    def withEvent(event: UserEvent, time: DateTime = now.minusDays(1)) = user.applyEvent(asEventInstant(event, time))

    def withWish(id: UUID = UUID.randomUUID(), time: DateTime = now): User =
      user.applyEvent(UserEventInstant(WishCreated(id, user.id, time), time))

    def withReservedWish(id: UUID = UUID.randomUUID(), reserver: UUID): User = {
      val events: List[UserEventInstant[_ <: UserEvent]] = EventsList(user.id).withReservedWish(id, "reserved wish", reserver).list
      events.foldLeft(user)(_.applyEvent(_))
    }

    def withDeletedWish(id: UUID = UUID.randomUUID(), time: DateTime = DateTime.now().minusDays(1)): User = withEvent(WishDeleted(id), time)

    def withExistingFriendRequest(reqId: UUID, from: UUID): User =
      user.applyEvent(UserEventInstant(FriendRequestReceived(user.id, from, Option(reqId)), now))

    def withFriendRequestNotification(notificationId: UUID, reqId: UUID, from: UUID): User =
      user.applyEvent(UserEventInstant(FriendRequestNotificationCreated(notificationId, user.id, from, reqId), now))

    def withFriend(friendId: UUID = UUID.randomUUID(), reqId: UUID = UUID.randomUUID()): User =
      user.
        withExistingFriendRequest(reqId, friendId).
        withFriendRequestNotification(UUID.randomUUID(), reqId, friendId).
        applyEvent(asEventInstant(FriendRequestStatusChanged(user.id, reqId, friendId, Approved)))

    def withSentFriendRequest(reqId: UUID, friend: UUID): User =
      user.applyEvent(asEventInstant(FriendRequestSent(user.id, friend, Option(reqId))))

    def withSentFriendRequestAccepted(reqId: UUID, friend: UUID): User =
      user.withSentFriendRequest(reqId, friend).
        applyEvent(asEventInstant(FriendRequestStatusChanged(user.id, reqId, user.id, Approved)))

    def withBirthday(birthday: LocalDate): User = withEvent(UserBirthdaySet(user.id, birthday.toString("MM/dd/yyyy")))
  }

}
