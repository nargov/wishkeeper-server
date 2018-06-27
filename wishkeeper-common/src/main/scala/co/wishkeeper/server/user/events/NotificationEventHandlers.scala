package co.wishkeeper.server.user.events

import java.util.UUID

import co.wishkeeper.server.Events.{WishReservedNotificationCreated, WishUnreservedNotificationCreated}
import co.wishkeeper.server.NotificationsData.{WishReservedNotification, WishUnreservedNotification}
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

  implicit val wishUnReservedNotificationHandler = new UserEventHandler[WishUnreservedNotificationCreated] {
    override def apply(user: User, event: WishUnreservedNotificationCreated, time: DateTime): User = {
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
  }

  implicit val wishReservedNotificationHandler = new UserEventHandler[WishReservedNotificationCreated] {
    override def apply(user: User, event: WishReservedNotificationCreated, time: DateTime): User = {
      val lastUnreserveNotification = aWishUnreservedNotificationFor(event.wishId)
      user.notifications.find(lastUnreserveNotification).map { unreserveNotification =>
        if(isTimeSinceLastReciprocalOverThreshold(unreserveNotification.time, time)) {
          if(overDelayThreshold(time))
            addWishReservedNotification(user, event, time)
          else
            addPendingWishReservedNotification(user, event, time)
        }
        else removeNotification(user, lastUnreserveNotification)
      }.getOrElse{
        if(overDelayThreshold(time))
          addWishReservedNotification(user, event, time)
        else
          addPendingWishReservedNotification(user, event, time)
      }
    }
  }
}