package co.wishkeeper.server.projections

import java.util.UUID
import java.util.UUID.randomUUID

import co.wishkeeper.server._
import co.wishkeeper.server.Events._
import co.wishkeeper.server.EventsTestHelper.{asEventInstants, userConnectEvent}
import com.wixpress.common.specs2.JMock
import org.joda.time.DateTime
import org.specs2.matcher.Matcher
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class DataStoreNotificationsProjectionTest extends Specification with JMock {

  trait Context extends Scope {
    val userId = randomUUID()
    val senderId = randomUUID()
    val dataStore = mock[DataStore]
    val projection = new DataStoreNotificationsProjection(dataStore)

    def processFriendRequest() = projection.process(FriendRequestSent(senderId, userId))

    def assumeExistingEvents() = checking {
      allowing(dataStore).lastSequenceNum(userId).willReturn(Some(5L))
    }
  }

  "save notification for incoming friend request" in new Context {
    assumeExistingEvents()

    checking {
      oneOf(dataStore).saveUserEvents(
        having(equalTo(userId)),
        having(any[Option[Long]]),
        having(any[DateTime]),
        having(contain(aFriendRequestNotificationCreatedEvent(userId, senderId))))
    }

    processFriendRequest()
  }

  "retrieve friend request notification with full data" in new Context {
    private val senderProfile = UserProfile(name = Option("Luke Skywalker"), picture = Option("http://example.com/mypicture"))
    val notificationId = randomUUID()

    checking {
      allowing(dataStore).userEvents(senderId).willReturn(asEventInstants(List(
        userConnectEvent(senderId),
        UserNameSet(senderId, senderProfile.name.get),
        UserPictureSet(senderId, senderProfile.picture.get)
      )))
      allowing(dataStore).userEvents(userId).willReturn(asEventInstants(List(
        userConnectEvent(senderId),
        FriendRequestNotificationCreated(notificationId, userId, senderId)
      )))
    }

    projection.notificationsFor(userId) must contain(Notification(
      notificationId, FriendRequestNotification(senderId, profile = Option(senderProfile))))
  }

  def aFriendRequestNotificationCreatedEvent(userId: UUID, from: UUID): Matcher[UserEvent] = (event: UserEvent) => (event match {
    case FriendRequestNotificationCreated(_, uId, sender) => uId == userId && sender == from
    case _ => false
  }, s"$event does not have userId $userId and from $from")

}
