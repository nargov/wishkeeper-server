package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.Events._
import co.wishkeeper.server.projections.DataStoreIncomingFriendRequestsProjection
import com.wixpress.common.specs2.JMock
import org.joda.time.DateTime
import org.specs2.matcher.Matcher
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class DataStoreIncomingFriendRequestsProjectionTest extends Specification with JMock {

  trait Context extends Scope {
    val userId = UUID.randomUUID()
    val senderId = UUID.randomUUID()
    val dataStore = mock[DataStore]
    val projection = new DataStoreIncomingFriendRequestsProjection(dataStore)
    def processFriendRequest() = projection.process(FriendRequestSent(senderId, userId))

    def assumeExistingEvents() = checking {
      allowing(dataStore).lastSequenceNum(userId).willReturn(Some(5L))
    }
  }

  "save incoming friend request" in new Context {
    assumeExistingEvents()
    checking {
      oneOf(dataStore).saveUserEvents(
        having(equalTo(userId)),
        having(any[Option[Long]]),
        having(any[DateTime]),
        having(contain(FriendRequestReceived(userId, senderId))))
    }

    processFriendRequest()

  }

  "return existing incoming friend requests" in new Context {
    checking {
      allowing(dataStore).userEvents(userId).willReturn(List(
        UserEventInstant(EventsTestHelper.randomSession(userId), DateTime.now()),
        UserEventInstant(FriendRequestReceived(userId, senderId), DateTime.now())
      ))
    }

    projection.awaitingApproval(userId) must beEqualTo(senderId :: Nil)
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

  def aFriendRequestNotificationCreatedEvent(userId: UUID, from: UUID): Matcher[UserEvent] = (event: UserEvent) => (event match {
    case FriendRequestNotificationCreated(_, uId, sender) => uId == userId && sender == from
    case _ => false
  }, s"$event does not have userId $userId and from $from")
}

object EventsTestHelper {
  def randomSession(userId: UUID) = UserConnected(userId, DateTime.now(), UUID.randomUUID())
}