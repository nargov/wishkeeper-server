package co.wishkeeper.server.user.events

import java.util.UUID

import co.wishkeeper.server.Events.WishUnreservedNotificationCreated
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

  private val isTimeSinceReserveOverThreshold: (DateTime, DateTime) => Boolean = (reserveTime, unreserveTime) =>
    reserveTime.isBefore(unreserveTime.minusMinutes(notificationDelayMinutes))

  val overDelayThreshold: DateTime => Boolean = time => time.isBefore(DateTime.now().minusMinutes(notificationDelayMinutes))

  private val getReserverId: Notification => Option[UUID] = {
    case Notification(_, WishReservedNotification(_, reserverId, _, _), _, _) => Option(reserverId)
    case _ => None
  }

  private val addWishUnreservedNotification: (User, WishUnreservedNotificationCreated, UUID, DateTime) => User = (user, event, reserver, time) =>
    user.copy(notifications = Notification(event.id, WishUnreservedNotification(event.wishId, reserver), time = time) :: user.notifications)

  private val removeWishReservedNotification: (User, NotificationFilter) => User = (user, filter) => {
    val reserveIndex = user.notifications.indexWhere(filter)
    user.copy(notifications = user.notifications.patch(reserveIndex, Nil, 1))
  }

  implicit val wishUnReservedNotificationHandler = new UserEventHandler[WishUnreservedNotificationCreated] {
    override def apply(user: User, event: WishUnreservedNotificationCreated, time: DateTime): User = {
      val theReserveNotification = aWishReservedNotificationFor(event.wishId)

      user.notifications.find(theReserveNotification).flatMap { reservedNotification =>
        getReserverId(reservedNotification).map { reserver =>
          if (isTimeSinceReserveOverThreshold(reservedNotification.time, time)) {
            if (overDelayThreshold(time))
              addWishUnreservedNotification(user, event, reserver, time)
            else user
          }
          else removeWishReservedNotification(user, theReserveNotification)
        }
      }.getOrElse(user)
    }
  }
}