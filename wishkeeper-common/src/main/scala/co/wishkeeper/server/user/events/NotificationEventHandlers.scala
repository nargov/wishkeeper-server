package co.wishkeeper.server.user.events

import java.util.UUID

import co.wishkeeper.server.Events.{FriendRequestAcceptedNotificationCreated, FriendRequestNotificationCreated, FriendRequestStatusChanged, NotificationViewed, WishReservedNotificationCreated, WishUnreservedNotificationCreated}
import co.wishkeeper.server.FriendRequestStatus.{Approved, Pending}
import co.wishkeeper.server.NotificationsData.{FriendRequestAcceptedNotification, FriendRequestNotification, WishReservedNotification, WishUnreservedNotification}
import co.wishkeeper.server.{Notification, User}
import org.joda.time.DateTime


object NotificationEventHandlers {
  val notificationDelayMinutes = 5

  type NotificationFilter = Notification => Boolean

  private val aWishReservedNotificationFor: UUID => NotificationFilter = wishId => {
    case Notification(_, WishReservedNotification(id, _, _, _), _, _) if id == wishId => true
    case _ => false
  }

  private val aWishUnreservedNotificationFor: UUID => NotificationFilter = wishId => {
    case Notification(_, WishUnreservedNotification(id, _, _, _), _, _) if id == wishId => true
    case _ => false
  }

  private val isTimeSinceLastReciprocalOverThreshold: (DateTime, DateTime) => Boolean = (firstTime, secondTime) =>
    firstTime.isBefore(secondTime.minusMinutes(notificationDelayMinutes))

  private val overDelayThreshold: DateTime => Boolean = time => time.isBefore(DateTime.now().minusMinutes(notificationDelayMinutes))

  private val getReserverId: Notification => Option[UUID] = {
    case Notification(_, WishReservedNotification(_, reserverId, _, _), _, _) => Option(reserverId)
    case _ => None
  }

  private val unreservedNotificationFor: (WishUnreservedNotificationCreated, UUID, DateTime) => Notification = (event, reserver, time) =>
    Notification(event.id, WishUnreservedNotification(event.wishId, reserver), time = time)

  private val addWishUnreservedNotification: (User, WishUnreservedNotificationCreated, UUID, DateTime) => User = (user, event, reserver, time) =>
    user.copy(notifications = unreservedNotificationFor(event, reserver, time) :: user.notifications)

  private val addPendingWishUnreservedNotification: (User, WishUnreservedNotificationCreated, UUID, DateTime) => User = (user, event, reserver, time) =>
    user.copy(pendingNotifications = unreservedNotificationFor(event, reserver, time) :: user.pendingNotifications)

  private val reservedNotificationFor: (WishReservedNotificationCreated, DateTime) => Notification = (event, time) =>
    Notification(event.id, WishReservedNotification(event.wishId, event.reserverId), time = time)

  private val addWishReservedNotification: (User, WishReservedNotificationCreated, DateTime) => User = (user, event, time) => {
    user.copy(notifications = reservedNotificationFor(event, time) :: user.notifications)
  }

  private val addPendingWishReservedNotification: (User, WishReservedNotificationCreated, DateTime) => User = (user, event, time) => {
    user.copy(pendingNotifications = reservedNotificationFor(event, time) :: user.pendingNotifications)
  }

  private val removeNotification: (User, NotificationFilter) => User = (user, filter) => {
    val reserveIndex = user.notifications.indexWhere(filter)
    user.copy(notifications = user.notifications.patch(reserveIndex, Nil, 1))
  }

  implicit val wishUnReservedNotificationHandler: UserEventHandler[WishUnreservedNotificationCreated] =
    (user: User, event: WishUnreservedNotificationCreated, time: DateTime) => {
      val lastReserveNotification = aWishReservedNotificationFor(event.wishId)

      user.notifications.find(lastReserveNotification).flatMap { reservedNotification =>
        getReserverId(reservedNotification).map { reserver =>
          if (isTimeSinceLastReciprocalOverThreshold(reservedNotification.time, time)) {
            if (overDelayThreshold(time))
              addWishUnreservedNotification(user, event, reserver, time)
            else
              addPendingWishUnreservedNotification(user, event, reserver, time)
          }
          else removeNotification(user, lastReserveNotification)
        }
      }.getOrElse(user)
    }

  implicit val wishReservedNotificationHandler: UserEventHandler[WishReservedNotificationCreated] =
    (user: User, event: WishReservedNotificationCreated, time: DateTime) => {
      val lastUnreserveNotification = aWishUnreservedNotificationFor(event.wishId)
      user.notifications.find(lastUnreserveNotification).map { unreserveNotification =>
        if (isTimeSinceLastReciprocalOverThreshold(unreserveNotification.time, time))
          scheduleReservedNotification(user, event, time)
        else
          removeNotification(user, lastUnreserveNotification)
      }.getOrElse {
        scheduleReservedNotification(user, event, time)
      }
    }

  private def scheduleReservedNotification(user: User, event: WishReservedNotificationCreated, time: DateTime): User = {
    if (overDelayThreshold(time))
      addWishReservedNotification(user, event, time)
    else
      addPendingWishReservedNotification(user, event, time)
  }

  implicit val friendRequestNotificationCreatedHandler: UserEventHandler[FriendRequestNotificationCreated] =
    (user: User, event: FriendRequestNotificationCreated, time: DateTime) => user.copy(notifications =
      Notification(event.id, FriendRequestNotification(event.from, event.requestId), time = time) :: user.notifications)

  implicit val friendRequestStatusChangedHandler: UserEventHandler[FriendRequestStatusChanged] =
    (user: User, event: FriendRequestStatusChanged, time: DateTime) => user.copy(
      friends = user.friends.copy(
        receivedRequests =
          if (event.from != user.id) user.friends.receivedRequests.filterNot(_.id == event.requestId)
          else user.friends.receivedRequests,
        sentRequests =
          if (event.from == user.id) user.friends.sentRequests.filterNot(_.id == event.requestId)
          else user.friends.sentRequests,
        current = event.status match {
          case Approved => if (event.from == user.id) {
            user.friends.sentRequests.find(_.id == event.requestId).map(_.userId).map(user.friends.current :+ _).getOrElse(user.friends.current)
          } else user.friends.current :+ event.from
          case _ => user.friends.current
        }),
      notifications = user.notifications.map {
        case n@Notification(_, notif@FriendRequestNotification(_, friendReqId, status, _), _, _) if friendReqId == event.requestId && status == Pending =>
          n.copy(data = notif.copy(status = event.status))
        case n => n
      }
    )

  implicit val friendRequestAcceptedNotificationCreatedHandler: UserEventHandler[FriendRequestAcceptedNotificationCreated] =
    (user: User, event: FriendRequestAcceptedNotificationCreated, time: DateTime) => user.copy(notifications =
      Notification(event.id, FriendRequestAcceptedNotification(event.by, event.requestId), time = time) :: user.notifications)

  implicit val notificationViewedHandler: UserEventHandler[NotificationViewed] = (user: User, event: NotificationViewed, time: DateTime) => {
    val index = user.notifications.indexWhere(_.id == event.id)
    if (index >= 0) {
      val updatedNotification = user.notifications(index).copy(viewed = true)
      user.copy(notifications = user.notifications.updated(index, updatedNotification))
    }
    else user
  }

}