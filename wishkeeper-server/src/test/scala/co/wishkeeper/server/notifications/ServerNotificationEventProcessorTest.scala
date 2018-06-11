package co.wishkeeper.server.notifications

import java.util.UUID
import java.util.UUID.randomUUID

import co.wishkeeper.server.Events._
import co.wishkeeper.server.FriendRequestStatus
import co.wishkeeper.server.FriendRequestStatus.Approved
import co.wishkeeper.server.messaging.{ClientNotifier, FriendsListUpdated, NotificationsUpdated, WishListUpdated}
import com.wixpress.common.specs2.JMock
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class ServerNotificationEventProcessorTest extends Specification with JMock {

  "Send wishlist updated notification when wish is reserved" in new Context {
    checkingWishListUpdateNotificationSent()

    processEvent(WishReserved(randomUUID(), randomUUID()))
  }

  "Send wishlist updated notification when wish is unreserved" in new Context {
    checkingWishListUpdateNotificationSent()

    processEvent(WishUnreserved(randomUUID()))
  }

  "Send friends list updated notification when friend request status changed" in new Context {
    checkingFriendsListUpdateNotificationSent()

//    processEvent(FriendRequestStatusChanged(randomUUID(), randomUUID(), userId, Approved))
    val friendId: UUID = randomUUID()
    processor.process(FriendRequestStatusChanged(friendId, randomUUID(), userId, Approved), friendId)
  }

  "Send notifications updated notification when friend request notification created" in new Context {
    checkingNotificationsUpdateNotificationSent()

    processEvent(FriendRequestNotificationCreated(randomUUID(), userId, randomUUID(), randomUUID()))
  }

  "Send notifications updated notification when friend request notification created" in new Context {
    checkingNotificationsUpdateNotificationSent()

    processEvent(FriendRequestAcceptedNotificationCreated(randomUUID(), userId, randomUUID(), randomUUID()))
  }

  "Send friends list updated notification when friend removed" in new Context {
    checkingFriendsListUpdateNotificationSent()

    processEvent(FriendRemoved(userId, randomUUID()))
  }

  "Send notifications updated when friend request received" in new Context {
    checkingFriendsListUpdateNotificationSent()

    processEvent(FriendRequestReceived(userId, randomUUID()))
  }


  trait Context extends Scope {
    val clientNotifier = mock[ClientNotifier]
    val processor = new ServerNotificationEventProcessor(clientNotifier)
    val userId = randomUUID()

    def checkingWishListUpdateNotificationSent() = checking {
      oneOf(clientNotifier).sendTo(having(WishListUpdated), having(===(userId)))
    }

    def checkingNotificationsUpdateNotificationSent() = checking {
      oneOf(clientNotifier).sendTo(having(NotificationsUpdated), having(===(userId)))
    }

    def checkingFriendsListUpdateNotificationSent() = checking {
      oneOf(clientNotifier).sendTo(having(FriendsListUpdated), having(===(userId)))
    }


    def processEvent(event: Event) = processor.process(event, userId) must beEmpty
  }

}
