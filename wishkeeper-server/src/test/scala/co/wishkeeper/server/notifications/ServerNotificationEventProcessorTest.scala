package co.wishkeeper.server.notifications

import java.util.UUID
import java.util.UUID.randomUUID

import co.wishkeeper.server.Events._
import co.wishkeeper.server.EventsTestHelper.EventsList
import co.wishkeeper.server.FriendRequestStatus.Approved
import co.wishkeeper.server.NotificationsData.{FriendRequestAcceptedNotification, FriendRequestNotification}
import co.wishkeeper.server.messaging._
import co.wishkeeper.server.{DataStore, PushNotification, UserProfile}
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

    val friendId: UUID = randomUUID()
    processor.process(FriendRequestStatusChanged(friendId, randomUUID(), userId, Approved), friendId)
  }

  "Send notifications updated notification when friend request notification created" in new Context {
    checkingNotificationsUpdateNotificationSent()
    val friendId: UUID = randomUUID()

    checking {
      ignoring(dataStore).userEvents(userId).willReturn(EventsList(userId).list)
    }

    processEvent(FriendRequestNotificationCreated(randomUUID(), userId, friendId, randomUUID()))
  }

  "Send notifications updated notification when friend request notification created" in new Context {
    checkingNotificationsUpdateNotificationSent()

    checking {
      ignoring(dataStore).userEvents(userId).willReturn(EventsList(userId).list)
    }

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

  "Send friend request notification through push" in new Context with Friend {
    val notificationId: UUID = randomUUID()

    checking {
      ignoring(clientNotifier)
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId).withDeviceId(deviceToken).
        withIncomingFriendRequest(friendId, requestId).list)
      allowing(dataStore).userEvents(friendId).willReturn(EventsList(friendId).withName(friendName).withFirstName(friendFirstName).
        withPic(friendPicture).list)
      oneOf(pushNotifications).send(deviceToken, PushNotification(userId, notificationId, FriendRequestNotification(friendId, requestId, profile = Option(UserProfile(
        name = Option(friendName), firstName = Option(friendFirstName), picture = Option(friendPicture)
      )))))
    }

    processEvent(FriendRequestNotificationCreated(notificationId, userId, friendId, requestId))
  }

  "Send friend request accepted notification through push" in new Context with Friend {
    val notificationId: UUID = randomUUID()

    checking {
      ignoring(clientNotifier)
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId).withDeviceId(deviceToken).withFriend(friendId, requestId).list)
      allowing(dataStore).userEvents(friendId).willReturn(EventsList(friendId).withName(friendName).withFirstName(friendFirstName).
        withPic(friendPicture).list)
      oneOf(pushNotifications).send(deviceToken, PushNotification(userId, notificationId, FriendRequestAcceptedNotification(friendId, requestId,
        profile = Option(UserProfile(name = Option(friendName), firstName = Option(friendFirstName), picture = Option(friendPicture))))))
    }

    processEvent(FriendRequestAcceptedNotificationCreated(notificationId, userId, friendId, requestId))
  }

  trait Friend {
    val friendName = "Friend Name"
    val friendFirstName = "Friend"
    val friendPicture = "pic"
    val friendId = randomUUID()
    val requestId = randomUUID()
  }

  trait Context extends Scope {
    val deviceToken = "id"
    val clientNotifier = mock[ClientNotifier]
    val dataStore = mock[DataStore]
    val pushNotifications = mock[PushNotifications]
    val processor = new ServerNotificationEventProcessor(clientNotifier, NoOpNotificationsScheduler, dataStore, pushNotifications)
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
