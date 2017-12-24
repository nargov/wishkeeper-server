package co.wishkeeper.server.projections

import java.util.UUID.randomUUID

import co.wishkeeper.server.DataStore
import co.wishkeeper.server.Events.{Event, FriendRemoved, FriendRequestStatusChanged}
import co.wishkeeper.server.FriendRequestStatus.Approved
import com.wixpress.common.specs2.JMock
import org.joda.time.DateTime
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class DataStoreFriendRequestsProjectionTest extends Specification with JMock {

  "return a FriendRequestStatusChanged when friend accepted" in new Context {
    assumeFriendPriorEventsExist()
    expectSavedEvent(FriendRequestStatusChanged(friendId, requestId, friendId, Approved))

    notificationsProjection.process(FriendRequestStatusChanged(userId, requestId, friendId, Approved))
  }

  "return a reciprocating FriendRemoved event" in new Context {
    assumeFriendPriorEventsExist()
    expectSavedEvent(FriendRemoved(friendId, userId))

    notificationsProjection.process(FriendRemoved(userId, friendId))
  }

  trait Context extends Scope {
    val userId = randomUUID()
    val friendId = randomUUID()
    val dataStore = mock[DataStore]
    val requestId = randomUUID()
    val notificationsProjection = new DataStoreFriendRequestsProjection(dataStore)

    def assumeFriendPriorEventsExist() = checking {
      allowing(dataStore).lastSequenceNum(friendId).willReturn(Some(5L))
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
