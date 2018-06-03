package co.wishkeeper.server.notifications

import java.util.UUID

import co.wishkeeper.server.Events._
import co.wishkeeper.server.messaging.{ClientNotifier, FriendsListUpdated, NotificationsUpdated, WishListUpdated}
import co.wishkeeper.server.{EventProcessor, Events}

class ServerNotificationEventProcessor(clientRegistry: ClientNotifier) extends EventProcessor {
  override def process(event: Events.Event, userId: UUID): Unit = event match {
    case _: WishReserved |
         _: WishUnreserved
    => clientRegistry.sendTo(WishListUpdated, userId)
    case _: FriendRequestStatusChanged |
         _: FriendRemoved
    => clientRegistry.sendTo(FriendsListUpdated, userId)
    case _: FriendRequestNotificationCreated |
         _: FriendRequestAcceptedNotificationCreated
    => clientRegistry.sendTo(NotificationsUpdated, userId)
    case _ =>
  }
}
