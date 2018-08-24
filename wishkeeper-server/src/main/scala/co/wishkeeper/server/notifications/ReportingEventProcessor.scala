package co.wishkeeper.server.notifications

import java.util.UUID
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.{Executors, ScheduledExecutorService}

import co.wishkeeper.server.Events.{FriendRequestStatusChanged, UserConnected, WishCreated, WishReserved}
import co.wishkeeper.server.FriendRequestStatus.Approved
import co.wishkeeper.server.notifications.ReportingEventProcessor.{delaySeconds, noDelay}
import co.wishkeeper.server.reporting.Reporter
import co.wishkeeper.server.{DataStore, EventProcessor, Events, User}
import org.joda.time.DateTime

class ReportingEventProcessor(reporter: Reporter, dataStore: DataStore,
                              scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(2)) extends EventProcessor {

  override def process(event: Events.Event, userId: UUID): List[(UUID, Events.Event)] = {
    val user = User.replay(dataStore.userEvents(userId))
    event match {
      case UserConnected(_, time, _) if user.created == time =>
        user.userProfile.name.fold(reportLater(userId, u => UserFirstConnection(userId, time, u.userProfile.name)))(name =>
          reportNow(UserFirstConnection(userId, time, Option(name))))
      case WishCreated(wishId, _, time) =>
        val wish = user.wishes(wishId)
        wish.name.fold(reportLater(userId, u => UserAddedWish(userId, user.userProfile.name, time, u.wishes(wishId).name)))(wishName =>
          reportNow(UserAddedWish(userId, user.userProfile.name, time, Option(wishName))))
      case WishReserved(wishId, reserverId) =>
        val reserver = User.replay(dataStore.userEvents(reserverId))
        reportNow(WishWasReserved(wishId, user.wishes(wishId).name, userId, user.userProfile.name, reserverId, reserver.userProfile.name))
      case FriendRequestStatusChanged(to, _, from, status) if status == Approved =>
        val friend = User.replay(dataStore.userEvents(if (to == userId) from else to))
        reportNow(UsersBecameFriends(userId, user.userProfile.name, to, friend.userProfile.name))
      case _ =>
    }
    Nil
  }

  private def reportNow(what: Any) = scheduler.schedule(() => reporter.report(what), noDelay, SECONDS)

  private def reportLater(userId: UUID, what: User => Any) = scheduler.schedule(() => {
    reporter.report(what(User.replay(dataStore.userEvents(userId))))
  }, delaySeconds, SECONDS)

}

object ReportingEventProcessor {
  val delaySeconds = 10
  val noDelay = 0
}

case class UserFirstConnection(userId: UUID, time: DateTime, name: Option[String])

case class UserAddedWish(userId: UUID, userName: Option[String], time: DateTime, name: Option[String])

case class WishWasReserved(wishId: UUID, wishName: Option[String], userId: UUID, userName: Option[String],
                           reserverId: UUID, reserverName: Option[String])

case class UsersBecameFriends(userId: UUID, userName: Option[String], friendId: UUID, friendName: Option[String])