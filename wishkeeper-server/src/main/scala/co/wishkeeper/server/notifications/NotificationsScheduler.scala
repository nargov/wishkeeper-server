package co.wishkeeper.server.notifications

import java.util.UUID
import java.util.concurrent.{Callable, Executors, ScheduledExecutorService}

import co.wishkeeper.server.messaging._
import co.wishkeeper.server.{DataStore, Notification, User}
import org.joda.time
import org.joda.time.DateTime

import scala.concurrent.duration._

trait NotificationsScheduler {
  def scheduleNotification(userId: UUID, notification: ServerNotification): Unit
}

class ExecutorNotificationsScheduler(config: NotificationDelayConfig = NotificationDelayConfig(),
                                     scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(5),
                                     clientNotifier: ClientNotifier,
                                     dataStore: DataStore) extends NotificationsScheduler {

  private def toCallable[T]: (() => T) => Callable[T] = f => () => f()

  def scheduleNotification(userId: UUID, notification: ServerNotification): Unit = {
    scheduler.schedule(toCallable(() => clientNotifier.sendTo(notification, userId)), config.default.toMillis, MILLISECONDS)
  }

  object NotificationOrdering extends Ordering[Notification] {
    override def compare(x: Notification, y: Notification): Int = x.time.compareTo(y.time)
  }

  private val clientNotifierListener: ClientRegistryEvent => Unit = {
    case UserConnectionAdded(userId) =>
      val pendingNotifications = User.replay(dataStore.userEvents(userId)).pendingNotifications
      pendingNotifications.foreach { notification =>
        scheduler.schedule(toCallable(() => clientNotifier.sendTo(NotificationsUpdated, userId)), delayDuration(notification), MILLISECONDS)
      }
    case UserConnectionRemoved(_) =>
  }

  private def delayDuration(notification: Notification): Long = {
    config.default.toMillis - new time.Duration(notification.time, DateTime.now()).getMillis
  }

  clientNotifier.addListener(clientNotifierListener)
}

object NoOpNotificationsScheduler extends NotificationsScheduler {
  override def scheduleNotification(userId: UUID, notification: ServerNotification): Unit = {}
}

case class NotificationDelayConfig(default: Duration = 5.minutes)