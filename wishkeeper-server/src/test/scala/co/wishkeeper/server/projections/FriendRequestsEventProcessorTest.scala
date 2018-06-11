package co.wishkeeper.server.projections

import java.util.UUID
import java.util.UUID.randomUUID

import co.wishkeeper.server.DataStore
import co.wishkeeper.server.Events._
import co.wishkeeper.server.FriendRequestStatus.Approved
import com.wixpress.common.specs2.JMock
import org.joda.time.DateTime
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class FriendRequestsEventProcessorTest extends Specification with JMock {

  "return a FriendRequestStatusChanged when friend accepted" in new Context {
    assumeFriendPriorEventsExist()
    val expectedEvent = FriendRequestStatusChanged(friendId, requestId, friendId, Approved)

    expectSavedEvent(expectedEvent)

    val statusChanged = FriendRequestStatusChanged(userId, requestId, friendId, Approved)
    notificationsProjection.process(statusChanged, userId)  must beEmpty
  }

  "return a reciprocating FriendRemoved event" in new Context {
    assumeFriendPriorEventsExist()

    val expectedEvent = FriendRemoved(friendId, userId)
    expectSavedEvent(expectedEvent)

    notificationsProjection.process(FriendRemoved(userId, friendId), userId) must beEqualTo(Nil)
  }

  "save incoming friend request" in new Context {
    val friendRequestSent = FriendRequestSent(friendId, userId, Option(requestId))

    assumeExistingEvents()
    val expectedEvent = FriendRequestReceived(userId, friendId, Option(requestId))
    checking {
      oneOf(dataStore).saveUserEvents(
        having(equalTo(userId)),
        having(any[Option[Long]]),
        having(any[DateTime]),
        having(contain(expectedEvent))).willReturn(true)
    }

    notificationsProjection.process(friendRequestSent, userId) must beEqualTo((userId, expectedEvent) :: Nil)
  }

  trait Context extends Scope {
    val userId = randomUUID()
    val friendId = randomUUID()
    val dataStore = mock[DataStore]
    val requestId = randomUUID()
    val notificationsProjection = new FriendRequestsEventProcessor(dataStore)

    def assumeFriendPriorEventsExist() = checking {
      allowing(dataStore).lastSequenceNum(friendId).willReturn(Some(5L))
    }

    def assumeExistingEvents() = checking {
      allowing(dataStore).lastSequenceNum(userId).willReturn(Some(5L))
    }

    def expectSavedEvent(event: Event) = checking {
      oneOf(dataStore).saveUserEvents(
        having(===(friendId)),
        having(any[Option[Long]]),
        having(any[DateTime]),
        having(contain(event))
      ).willReturn(true)
    }
  }

}
