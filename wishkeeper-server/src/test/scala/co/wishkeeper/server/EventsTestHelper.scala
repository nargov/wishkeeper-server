package co.wishkeeper.server

import java.util.UUID
import java.util.UUID.randomUUID

import co.wishkeeper.server.Events._
import co.wishkeeper.server.FriendRequestStatus.Approved
import co.wishkeeper.server.UserEventInstant.UserEventInstants
import org.joda.time.DateTime

object EventsTestHelper {
  def userConnectEvent(userId: UUID, time: DateTime = DateTime.now()) = UserConnected(userId, time, randomUUID())

  def asEventInstants(events: List[UserEvent], time: DateTime = DateTime.now()): UserEventInstants = events.map(asEventInstant(_, time))

  def asEventInstances(events: List[(UUID, UserEvent)]): List[UserEventInstance[_ <: UserEvent]] = events.map(e => asEventInstance(e._1, e._2))

  def asEventInstant(event: UserEvent, time: DateTime = DateTime.now().minusDays(1)) = UserEventInstant(event, time)

  def asEventInstance(userId: UUID, event: UserEvent, time: DateTime = DateTime.now.minusDays(1)) = UserEventInstance(userId, event, time)

  def anEventsListFor(userId: UUID) = EventsList(userId)

  case class EventsList(userId: UUID, list: List[UserEventInstant[_ <: UserEvent]]) {

    def withFriends(friends: Seq[Friend]): EventsList = friends.foldLeft(this)(_.withFriend(_))

    def withFriend(friend: Friend): EventsList = withFriend(friend.userId)

    def withFriend(friendId: UUID, requestId: UUID = randomUUID()): EventsList = this.copy(list = list ++ asEventInstants(List(
      FriendRequestSent(userId, friendId, Option(requestId)),
      FriendRequestStatusChanged(userId, requestId, userId, Approved)
    )))

    def withFriendRequest(friendId: UUID, requestId: UUID = randomUUID()): EventsList =
      this.copy(list = list :+ asEventInstant(FriendRequestSent(userId, friendId, Option(requestId))))

    def withIncomingFriendRequest(friendId: UUID, friendRequestId: UUID): EventsList =
      this.copy(list = list :+ asEventInstant(FriendRequestReceived(userId, friendId, Option(friendRequestId))))

    def withName(name: String): EventsList = withEvent(UserNameSet(userId, name))

    def withName(name: Option[String]): EventsList = name.fold(this)(withName)

    def withFirstName(name: String): EventsList = withEvent(UserFirstNameSet(userId, name))

    def withLastName(name: String): EventsList = withEvent(UserLastNameSet(userId, name))

    def withWish(id: UUID, name: String, time: DateTime = DateTime.now().minusDays(1)): EventsList =
      this.copy(list = list ++ asEventInstants(List(
        WishCreated(id, userId, time),
        WishNameSet(id, name)
      ), time))

    def withReservedWish(id: UUID, name: String, reserver: UUID): EventsList = {
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

    def withEvent(event: UserEvent, time: DateTime = DateTime.now()): EventsList = copy(list = list :+ asEventInstant(event, time))

    def withEmail(email: String): EventsList = withEvent(UserEmailSet(userId, email))

    def withPic(link: String): EventsList = this.copy(list = list :+ asEventInstant(UserPictureSet(userId, link)))

    def withDeviceId(id: String): EventsList = withEvent(DeviceNotificationIdSet(id))

    def withBirthday(day: String): EventsList = withEvent(UserBirthdaySet(userId, day))
  }

  object EventsList {
    def apply(userId: UUID, created: DateTime = DateTime.now()): EventsList =
      EventsList(userId, asEventInstant(userConnectEvent(userId, created), created) :: Nil)
  }

}
