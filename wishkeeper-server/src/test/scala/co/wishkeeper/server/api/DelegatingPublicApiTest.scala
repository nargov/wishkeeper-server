package co.wishkeeper.server.api

import java.util.UUID
import java.util.UUID.randomUUID

import co.wishkeeper.server.Events.{FacebookFriendsListSeen, FriendRequestNotificationCreated, UserConnected}
import co.wishkeeper.server.EventsTestHelper.{asEventInstants, userConnectEvent}
import co.wishkeeper.server._
import co.wishkeeper.server.projections.NotificationsProjection
import com.wixpress.common.specs2.JMock
import org.joda.time.DateTime
import org.specs2.matcher.Matcher
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class DelegatingPublicApiTest extends Specification with JMock {

  trait Context extends Scope {
    val sessionId: UUID = randomUUID()
    val userId: UUID = randomUUID()
    val dataStore: DataStore = mock[DataStore]
    val notificationsProjection = mock[NotificationsProjection]
    val api: PublicApi = new DelegatingPublicApi(null, dataStore, null, null, null, null, notificationsProjection, null)(null, null, null)

    def assumingUserHasSession() = checking {
      allowing(dataStore).userBySession(sessionId).willReturn(Option(userId))
    }
  }

  "returns the flags for user by the given session" in new Context {
    assumingUserHasSession()

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

  "returns user notifications" in new Context {
    val sender: UUID = randomUUID()
    private val notificationData = FriendRequestNotification(sender)
    assumingUserHasSession()

    checking {
      allowing(notificationsProjection).notificationsFor(userId).willReturn(List(Notification(randomUUID(), notificationData)))
    }

    api.userNotificationsFor(sessionId) must contain(aNotificationWith(notificationData, viewed = false))
  }

  def aNotificationWith(data: NotificationData, viewed: Boolean): Matcher[Notification] = (notification: Notification) =>
    (notification.data == data && viewed == notification.viewed,
      s"$notification does not match $data and viewed[$viewed]")
}
