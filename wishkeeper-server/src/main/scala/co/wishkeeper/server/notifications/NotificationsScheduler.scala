package co.wishkeeper.server.notifications

import java.util.UUID
import java.util.concurrent.{Callable, Executors, ScheduledExecutorService}

import co.wishkeeper.server.messaging._
import co.wishkeeper.server.{DataStore, Notification, PushNotification, User}
import org.joda.time
import org.joda.time.DateTime

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Success, Try}

trait NotificationsScheduler {
  def scheduleNotification(userId: UUID, notification: ServerNotification): Unit

  def schedulePushNotification(deviceId: String, notification: PushNotification): Future[Try[String]]
}

class ExecutorNotificationsScheduler(config: NotificationDelayConfig = NotificationDelayConfig(),
                                     scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(10),
                                     clientNotifier: ClientNotifier,
                                     dataStore: DataStore,
                                     pushNotifications: PushNotifications) extends NotificationsScheduler {

  implicit val ec = ExecutionContext.fromExecutorService(scheduler)

  private def toCallable[T]: (() => T) => Callable[T] = f => () => f()

  def scheduleNotification(userId: UUID, notification: ServerNotification): Unit = {
    scheduler.schedule(toCallable(() => {
      clientNotifier.sendTo(notification, userId)
    }), config.default.toMillis, MILLISECONDS)
  }

  override def schedulePushNotification(deviceId: String, notification: PushNotification): Future[Try[String]] =
    Future {
      scheduler.schedule(toCallable(() => pushNotifications.send(deviceId, notification)), config.default.toMillis, MILLISECONDS).get()
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

  override def schedulePushNotification(deviceId: String, notification: PushNotification): Future[Try[String]] = Future.successful(Success(""))
}

case class NotificationDelayConfig(default: Duration = 5.minutes)