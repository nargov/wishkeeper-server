package co.wishkeeper.server.notifications

import java.util.UUID

import co.wishkeeper.server.Events._
import co.wishkeeper.server.NotificationsData._
import co.wishkeeper.server._
import co.wishkeeper.server.messaging._

class ServerNotificationEventProcessor(notifier: ClientNotifier,
                                       scheduler: NotificationsScheduler,
                                       dataStore: DataStore,
                                       pushNotifications: PushNotifications)
  extends EventProcessor {

  override def process(event: Events.Event, userId: UUID): List[(UUID, Event)] = {
    event match {
      case _: WishReserved |
           _: WishUnreserved =>
        notifier.sendTo(WishListUpdated, userId)
      case FriendRequestStatusChanged(_, _, from, _) =>
        notifier.sendTo(FriendsListUpdated, from)
      case _: FriendRemoved |
           _: FriendRequestReceived =>
        notifier.sendTo(FriendsListUpdated, userId)
      case n: FriendRequestNotificationCreated =>
        notifier.sendTo(NotificationsUpdated, userId)
        val user = User.replay(dataStore.userEvents(userId))
        user.settings.deviceNotificationId.foreach { deviceId =>
          val friendProfile = profileForNotification(User.replay(dataStore.userEvents(n.from)))
          pushNotifications.send(deviceId, PushNotification(FriendRequestNotification(n.from, n.requestId, profile = friendProfile)))
        }
      case n: FriendRequestAcceptedNotificationCreated =>
        notifier.sendTo(NotificationsUpdated, userId)
        val user = User.replay(dataStore.userEvents(userId))
        user.settings.deviceNotificationId.foreach { deviceId =>
          val friendProfile = profileForNotification(User.replay(dataStore.userEvents(n.by)))
          pushNotifications.send(deviceId, PushNotification(FriendRequestAcceptedNotification(n.by, n.requestId, profile = friendProfile)))
        }
      case n: WishReservedNotificationCreated =>
        scheduler.scheduleNotification(userId, NotificationsUpdated)
        schedulePushNotification(userId, user => {
          val wishName = user.wishes(n.wishId).name
          WishReservedNotification(n.wishId, n.reserverId, wishName = wishName)
        })
      case n: WishUnreservedNotificationCreated =>
        scheduler.scheduleNotification(userId, NotificationsUpdated)
        schedulePushNotification(userId, user => {
          val wishName = user.wishes(n.wishId).name
          WishUnreservedNotification(n.wishId, null, wishName = wishName)
        })
      case _ =>
    }
    Nil
  }

  private val profileForNotification: User => Option[UserProfile] = user => {
    val profile = user.userProfile
    Option(UserProfile(name = profile.name, firstName = profile.firstName, picture = profile.picture))
  }

  private def schedulePushNotification(userId: UUID, notificationCreator: User => NotificationData) = {
    val user = User.replay(dataStore.userEvents(userId))
    user.settings.deviceNotificationId.foreach { deviceId =>
      scheduler.schedulePushNotification(deviceId, PushNotification(notificationCreator(user)))
    }
  }
}
