package co.wishkeeper.server.projections

import java.util.UUID
import java.util.UUID.randomUUID

import co.wishkeeper.server.Events._
import co.wishkeeper.server.EventsTestHelper.{EventsList, asEventInstants, userConnectEvent}
import co.wishkeeper.server.FriendRequestStatus.{Approved, Ignored}
import co.wishkeeper.server.NotificationsData.{FriendRequestAcceptedNotification, FriendRequestNotification}
import co.wishkeeper.server._
import com.wixpress.common.specs2.JMock
import org.joda.time.DateTime
import org.specs2.matcher.Matcher
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class DataStoreNotificationsProjectionTest extends Specification with JMock {

  "save notification for incoming friend request" in new Context {
    assumeExistingEvents()

    checking {
      oneOf(dataStore).saveUserEvents(
        having(equalTo(userId)),
        having(any[Option[Long]]),
        having(any[DateTime]),
        having(contain(aFriendRequestNotificationCreatedEvent(userId, friendId)))).willReturn(true)
    }

    processFriendRequest()
  }

  "retrieve friend request notification with full data" in new Context {
    assumingFriendExists()

    checking {
      allowing(dataStore).userEvents(userId).willReturn(asEventInstants(List(
        userConnectEvent(userId),
        FriendRequestNotificationCreated(notificationId, userId, friendId, friendReqId)
      )))
    }

    projection.notificationsFor(userId) must contain(notificationIgnoringTime(Notification(
      notificationId, FriendRequestNotification(friendId, requestId = friendReqId, profile = Option(friendProfile)))))
  }

  "retrieve friend request accepted notification with full data" in new Context {
    assumingFriendExists()

    checking {
      allowing(dataStore).userEvents(userId).willReturn(asEventInstants(List(
        userConnectEvent(userId),
        FriendRequestAcceptedNotificationCreated(notificationId, userId, friendId, friendReqId)
      )))
    }

    projection.notificationsFor(userId) must contain(notificationIgnoringTime(Notification(
      notificationId, FriendRequestAcceptedNotification(friendId, friendReqId, profile = Option(friendProfile)))))
  }

  "return a FriendRequestAcceptedNotificationCreated when friend request status changes to approved" in new Context {
    assumeExistingEvents()

    checking {
      oneOf(dataStore).saveUserEvents(
        having(equalTo(friendId)),
        having(any[Option[Long]]),
        having(any[DateTime]),
        having(contain(aFriendRequesdAcceptedNotificationCreatedEvent(friendId, userId, friendReqId)))).willReturn(true)
    }

    projection.process(FriendRequestStatusChanged(userId, friendReqId, friendId, Approved))
  }

  "not return a FriendRequestAcceptedNotificationCreated when friend request status changes to ignored" in new Context {
    projection.process(FriendRequestStatusChanged(userId, friendReqId, friendId, Ignored))
  }

  "not return notifications for wishes that have been deleted" in new Context {
    val wishId = randomUUID()

    checking {
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId).
        withDeletedWish(wishId).
        withEvent(WishReservedNotificationCreated(randomUUID(), wishId, randomUUID())).
        withEvent(WishUnreservedNotificationCreated(randomUUID(), wishId)).
        list)
    }

    projection.notificationsFor(userId) must beEmpty
  }


  trait Context extends Scope {
    val userId = randomUUID()
    val friendId = randomUUID()
    val friendReqId = randomUUID()
    val dataStore = mock[DataStore]
    val projection = new DataStoreNotificationsProjection(dataStore)
    val friendProfile = UserProfile(name = Option("Luke Skywalker"), picture = Option("http://example.com/mypicture"))
    val notificationId = randomUUID()

    def processFriendRequest() = projection.process(FriendRequestSent(friendId, userId, Option(friendReqId)))

    def assumeExistingEvents() = checking {
      allowing(dataStore).lastSequenceNum(having(any[UUID])).willReturn(Some(5L))
    }

    def assumingFriendExists() = checking {
      checking {
        allowing(dataStore).userEvents(friendId).willReturn(asEventInstants(List(
          userConnectEvent(friendId),
          UserNameSet(friendId, friendProfile.name.get),
          UserPictureSet(friendId, friendProfile.picture.get)
        )))
      }
    }
  }

  def aFriendRequesdAcceptedNotificationCreatedEvent(userId: UUID, by: UUID, reqId: UUID): Matcher[UserEvent] =
    (event: UserEvent) => event match {
      case e: FriendRequestAcceptedNotificationCreated => e.userId == userId && e.by == by && e.requestId == reqId
      case _ => false
    }

  def aFriendRequestNotificationCreatedEvent(userId: UUID, from: UUID): Matcher[UserEvent] = (event: UserEvent) => (event match {
    case FriendRequestNotificationCreated(_, uId, sender, reqId) => uId == userId && sender == from
    case _ => false
  }, s"$event does not have userId $userId and from $from")

  def notificationIgnoringTime = (===(_: Notification)) ^^^ ((_: Notification).copy(time = new DateTime(0)))

}
