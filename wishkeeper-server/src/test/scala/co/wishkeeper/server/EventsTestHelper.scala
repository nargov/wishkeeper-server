package co.wishkeeper.server

import java.util.UUID
import java.util.UUID.randomUUID

import co.wishkeeper.server.Events._
import co.wishkeeper.server.FriendRequestStatus.Approved
import co.wishkeeper.server.projections.Friend
import org.joda.time.DateTime

object EventsTestHelper {
  def userConnectEvent(userId: UUID) = UserConnected(userId, DateTime.now(), randomUUID())

  def asEventInstants(events: List[UserEvent]): List[UserEventInstant[_ <: UserEvent]] = events.map(asEventInstant(_))

  def asEventInstances(events: List[(UUID, UserEvent)]): List[UserEventInstance[_ <: UserEvent]] = events.map(e => asEventInstance(e._1, e._2))

  def asEventInstant(event: UserEvent, time: DateTime = DateTime.now().minusDays(1)) = UserEventInstant(event, time)

  def asEventInstance(userId: UUID, event: UserEvent, time: DateTime = DateTime.now.minusDays(1)) = UserEventInstance(userId, event, time)

  def anEventsListFor(userId: UUID) = EventsList(userId)

  case class EventsList(userId: UUID, list: List[UserEventInstant[_ <: UserEvent]]) {

    def withFriends(friends: Seq[Friend]): EventsList = friends.foldLeft(this)(_.withFriend(_))

    def withFriend(friend: Friend): EventsList = withFriend(friend.userId)

    def withFriend(friendId: UUID, requestId: UUID = randomUUID()) = this.copy(list = list ++ asEventInstants(List(
      FriendRequestSent(userId, friendId, Option(requestId)),
      FriendRequestStatusChanged(userId, requestId, userId, Approved)
    )))

    def withFriendRequest(friendId: UUID, requestId: UUID = randomUUID()) =
      this.copy(list = list :+ asEventInstant(FriendRequestSent(userId, friendId, Option(requestId))))

    def withIncomingFriendRequest(friendId: UUID, friendRequestId: UUID) =
      this.copy(list = list :+ asEventInstant(FriendRequestReceived(userId, friendId, Option(friendRequestId))))

    def withName(name: String): EventsList = withEvent(UserNameSet(userId, name))

    def withName(name: Option[String]): EventsList = name.fold(this)(withName)

    def withFirstName(name: String) = withEvent(UserFirstNameSet(userId, name))

    def withLastName(name: String) = withEvent(UserLastNameSet(userId, name))

    def withWish(id: UUID, name: String) = this.copy(list = list ++ asEventInstants(List(
      WishCreated(id, userId, DateTime.now().minusDays(1)),
      WishNameSet(id, name)
    )))

    def withReservedWish(id: UUID, name: String, reserver: UUID) = {
      val listWithName = this.withWish(id, name)
      listWithName.copy(list = listWithName.list :+ asEventInstant(WishReserved(id, reserver)))
    }

    def withReservedWishNotification(notificationId: UUID, wishId: UUID, reserverId: UUID, time: DateTime = DateTime.now().minusDays(1)) =
      this.copy(list = list :+ asEventInstant(WishReservedNotificationCreated(notificationId, wishId, reserverId), time))

    def withUnreservedWishNotification(notificationId: UUID, wishId: UUID, time: DateTime): EventsList =
      this.copy(list = list :+ asEventInstant(WishUnreservedNotificationCreated(notificationId, wishId), time))

    def withDeletedWish(id: UUID, name: String = "some wish"): EventsList = {
      val listWithName = this.withWish(id, name)
      listWithName.copy(list = listWithName.list :+ asEventInstant(WishDeleted(id)))
    }

    def withEvent(event: UserEvent): EventsList = copy(list = list :+ asEventInstant(event))

    def withPic(link: String) = this.copy(list = list :+ asEventInstant(UserPictureSet(userId, link)))

    def withDeviceId(id: String) = withEvent(DeviceNotificationIdSet(id))
  }

  object EventsList {
    def apply(userId: UUID): EventsList = EventsList(userId, asEventInstants(List(userConnectEvent(userId))))
  }

}
