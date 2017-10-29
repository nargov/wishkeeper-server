package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.Events._
import co.wishkeeper.server.FriendRequestStatus.Approved
import org.joda.time.DateTime

object EventsTestHelper {
  def userConnectEvent(userId: UUID) = UserConnected(userId, DateTime.now(), UUID.randomUUID())

  def asEventInstants(events: List[UserEvent]): List[UserEventInstant] = events.map(asEventInstant)

  def asEventInstant(event: UserEvent) = UserEventInstant(event, DateTime.now().minusDays(1))

  def anEventsListFor(userId: UUID) = EventsList(userId)

  case class EventsList(userId: UUID, list: List[UserEventInstant]) {
    def withFriend(friendId: UUID, requestId: UUID) = this.copy(list = list ++ asEventInstants(List(
      FriendRequestSent(userId, friendId, Option(requestId)),
      FriendRequestStatusChanged(userId, requestId, userId, Approved)
    )))

    def withName(name: String) = this.copy(list = list :+ asEventInstant(UserNameSet(userId, name)))
    def withWish(id: UUID, name: String) = this.copy(list = list ++ asEventInstants(List(
      WishCreated(id, userId, DateTime.now().minusDays(1)),
      WishNameSet(id, name)
    )))
  }
  object EventsList{
    def apply(userId: UUID): EventsList = EventsList(userId, asEventInstants(List(userConnectEvent(userId))))
  }
}
