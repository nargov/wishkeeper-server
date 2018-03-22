package co.wishkeeper.server.user.events

import java.util.UUID

import co.wishkeeper.server.Events.WishUnreservedNotificationCreated
import co.wishkeeper.server.NotificationsData.{WishReservedNotification, WishUnreservedNotification}
import co.wishkeeper.server.{Notification, User}
import org.joda.time.DateTime


object NotificationEventHandlers {
  val notificationDelayMinutes = 5

  private val timeSinceReserveOverThreshold: (User, DateTime, UUID) => Boolean = (user, time, wishId) => {
    user.notifications.find {
      case Notification(_, WishReservedNotification(id, _, _, _), _, _) if id == wishId => true
      case _ => false
    }.exists(_.time.isBefore(time.minusMinutes(notificationDelayMinutes)))
  }

  val overDelayThreshold: DateTime => Boolean = time => time.isBefore(DateTime.now().minusMinutes(notificationDelayMinutes))

  implicit val wishUnReservedNotificationHandler = new UserEventHandler[WishUnreservedNotificationCreated] {
    override def apply(user: User, event: WishUnreservedNotificationCreated, time: DateTime): User = {
      if (overDelayThreshold(time)) {
        val maybeReserver: Option[UUID] = user.notifications.find {
          case Notification(_, WishReservedNotification(id, _, _, _), _, _) if id == event.wishId => true
          case _ => false
        }.map{
          case Notification(_, WishReservedNotification(_, reserverId, _, _), _, _) => reserverId
        }
        maybeReserver.map{ reserver =>
          if (timeSinceReserveOverThreshold(user, time, event.wishId)) {
            user.copy(notifications = Notification(
              event.id,
              WishUnreservedNotification(event.wishId, reserver),
              time = time) :: user.notifications)
          }
          else {
            val lastReserveIndex = user.notifications.indexWhere {
              case Notification(_, WishReservedNotification(wishId, _, _, _), _, _) if wishId == event.wishId => true
              case _ => false
            }
            user.copy(notifications = user.notifications.patch(lastReserveIndex, Nil, 1))
          }
        }.getOrElse(user)
      }
      else user
    }
  }
}
