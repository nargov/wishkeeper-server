package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.Events._
import co.wishkeeper.server.FriendRequestStatus.Approved
import org.joda.time.DateTime

object EventsTestHelper {
  def userConnectEvent(userId: UUID) = UserConnected(userId, DateTime.now(), UUID.randomUUID())

  def asEventInstants(events: List[UserEvent]): List[UserEventInstant[_ <: UserEvent]] = events.map(asEventInstant(_))

  def asEventInstant(event: UserEvent, time: DateTime = DateTime.now().minusDays(1)) = UserEventInstant(event, time)

  def anEventsListFor(userId: UUID) = EventsList(userId)

  case class EventsList(userId: UUID, list: List[UserEventInstant[_ <: UserEvent]]) {

    def withFriend(friendId: UUID, requestId: UUID = UUID.randomUUID()) = this.copy(list = list ++ asEventInstants(List(
      FriendRequestSent(userId, friendId, Option(requestId)),
      FriendRequestStatusChanged(userId, requestId, userId, Approved)
    )))

    def withFriendRequest(friendId: UUID, requestId: UUID = UUID.randomUUID()) =
      this.copy(list = list :+ asEventInstant(FriendRequestSent(userId, friendId, Option(requestId))))

    def withIncomingFriendRequest(friendId: UUID, friendRequestId: UUID) =
      this.copy(list = list :+ asEventInstant(FriendRequestReceived(userId, friendId, Option(friendRequestId))))

    def withName(name: String) = this.copy(list = list :+ asEventInstant(UserNameSet(userId, name)))

    def withWish(id: UUID, name: String) = this.copy(list = list ++ asEventInstants(List(
      WishCreated(id, userId, DateTime.now().minusDays(1)),
      WishNameSet(id, name)
    )))

    def withReservedWish(id: UUID, name: String, reserver: UUID) = {
      val listWithName = this.withWish(id, name)
      listWithName.copy(list = listWithName.list :+ asEventInstant(WishReserved(id, reserver)))
    }

    def withDeletedWish(id: UUID, name: String = "some wish"): EventsList = {
      val listWithName = this.withWish(id, name)
      listWithName.copy(list = listWithName.list :+ asEventInstant(WishDeleted(id)))
    }

    def withEvent(event: UserEvent): EventsList = {
      this.copy(list = this.list :+ asEventInstant(event))
    }

    def withPic(link: String) = this.copy(list = list :+ asEventInstant(UserPictureSet(userId, link)))
  }

  object EventsList {
    def apply(userId: UUID): EventsList = EventsList(userId, asEventInstants(List(userConnectEvent(userId))))
  }

}
