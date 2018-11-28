package co.wishkeeper.server.projections

import java.util.UUID.randomUUID

import co.wishkeeper.server.Events._
import co.wishkeeper.server.EventsTestHelper.EventsList
import co.wishkeeper.server.FriendRequestStatus.Approved
import co.wishkeeper.server.UserEventTestHelpers.aUserEventInstance
import co.wishkeeper.server.{DataStore, UserEventInstance, UserEventTestHelpers}
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
    notificationsProjection.process(UserEventInstance(userId, statusChanged))  must beEmpty
  }

  "return a reciprocating FriendRemoved event" in new Context {
    assumeFriendPriorEventsExist()

    val expectedEvent = FriendRemoved(friendId, userId)
    checking {
      allowing(dataStore).userEvents(friendId).willReturn(EventsList(friendId).withFriend(userId).list)
    }
    expectSavedEvent(expectedEvent)

    notificationsProjection.process(UserEventInstance(userId, FriendRemoved(userId, friendId))) must
      contain(aUserEventInstance(===(expectedEvent), ===(friendId)))
  }

  "Not create a FriendRemoved event if not friends" in new Context {
    assumeFriendPriorEventsExist()

    val expectedEvent = FriendRemoved(friendId, userId)
    checking {
      allowing(dataStore).userEvents(friendId).willReturn(EventsList(friendId).list)
    }

    notificationsProjection.process(UserEventInstance(userId, FriendRemoved(userId, friendId))) must beEqualTo(Nil)
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

    notificationsProjection.process(UserEventInstance(userId, friendRequestSent)) must
      contain(aUserEventInstance(===(expectedEvent), ===(userId)))
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

    def expectSavedEvent(event: UserEvent) = checking {
      oneOf(dataStore).saveUserEvents(
        having(===(friendId)),
        having(any[Option[Long]]),
        having(any[DateTime]),
        having(contain(event))
      ).willReturn(true)
    }
  }

}
