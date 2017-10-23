package co.wishkeeper.server.api

import java.util.UUID
import java.util.UUID.randomUUID

import co.wishkeeper.server.Commands.ChangeFriendRequestStatus
import co.wishkeeper.server.Events.{FacebookFriendsListSeen, UserConnected}
import co.wishkeeper.server.EventsTestHelper.EventsList
import co.wishkeeper.server.FriendRequestStatus.{Approved, Ignored}
import co.wishkeeper.server.NotificationsData.{FriendRequestNotification, NotificationData}
import co.wishkeeper.server._
import co.wishkeeper.server.projections.{NotificationsProjection, ReplayingUserProfileProjection, UserProfileProjection}
import com.wixpress.common.specs2.JMock
import org.joda.time.DateTime
import org.specs2.matcher.Matcher
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class DelegatingPublicApiTest extends Specification with JMock {

  "returns the flags for user by the given session" in new LoggedInContext {
    checking {
      allowing(dataStore).userEvents(userId).willReturn(List(
        UserEventInstant(UserConnected(userId, sessionId = sessionId), DateTime.now()),
        UserEventInstant(FacebookFriendsListSeen(), DateTime.now())
      ))
    }

    api.userFlagsFor(sessionId).seenFacebookFriendsList must beTrue
  }

  "throws a SessionNotFoundException if session is not found" in new Context {
    checking {
      allowing(dataStore).userBySession(sessionId).willReturn(None)
    }

    api.userFlagsFor(sessionId) must throwA[SessionNotFoundException]
  }

  "returns user notifications" in new LoggedInContext {
    val notifications = List(Notification(randomUUID(), notificationData))

    checking {
      allowing(notificationsProjection).notificationsFor(userId).willReturn(notifications)
    }

    api.notificationsFor(sessionId) must beEqualTo(UserNotifications(notifications, unread = 1))
  }

  "approve friend request" in new LoggedInContext {
    checking {
      oneOf(commandProcessor).process(ChangeFriendRequestStatus(friendRequestId, Approved), userId)
    }

    api.approveFriendRequest(sessionId, friendRequestId)
  }

  "ignore friend request" in new LoggedInContext {
    checking {
      oneOf(commandProcessor).process(ChangeFriendRequestStatus(friendRequestId, Ignored), userId)
    }

    api.ignoreFriendRequest(sessionId, friendRequestId)
  }

  "return friend profile" in new LoggedInContext {
    val friendName = "Joe"

    checking {
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId).withFriend(friendId, friendRequestId).list)
      allowing(dataStore).userEvents(friendId).willReturn(EventsList(friendId).withName(friendName).list)
    }

    api.userProfileFor(sessionId, friendId) must beRight(UserProfile(name = Some(friendName)))
  }

  "return error when not friends" in new LoggedInContext {
    checking {
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId).list)
    }
    api.userProfileFor(sessionId, friendId) must beLeft[Reason](NotFriends)
  }

  trait Context extends Scope {
    val sessionId: UUID = randomUUID()
    val userId: UUID = randomUUID()
    val dataStore: DataStore = mock[DataStore]
    val notificationsProjection = mock[NotificationsProjection]
    val commandProcessor = mock[CommandProcessor]
    val userProfileProjection: UserProfileProjection = new ReplayingUserProfileProjection(dataStore)
    val api: PublicApi = new DelegatingPublicApi(
      commandProcessor,
      dataStore, null, null, userProfileProjection, null, notificationsProjection, null)(null, null, null)
    val friendId: UUID = randomUUID()
    val friendRequestId = randomUUID()
    val notificationData = FriendRequestNotification(friendId, friendRequestId)
  }

  trait LoggedInContext extends Context {
    checking {
      allowing(dataStore).userBySession(sessionId).willReturn(Option(userId))
    }
  }

  def aNotificationWith(data: NotificationData, viewed: Boolean): Matcher[Notification] = (notification: Notification) =>
    (notification.data == data && viewed == notification.viewed,
      s"$notification does not match $data and viewed[$viewed]")
}
