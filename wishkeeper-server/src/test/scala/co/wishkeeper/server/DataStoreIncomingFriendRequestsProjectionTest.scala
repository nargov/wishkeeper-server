package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.Events._
import co.wishkeeper.server.projections.DataStoreIncomingFriendRequestsProjection
import com.wixpress.common.specs2.JMock
import org.joda.time.DateTime
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class DataStoreIncomingFriendRequestsProjectionTest extends Specification with JMock {

  trait Context extends Scope {
    val userId = UUID.randomUUID()
    val senderId = UUID.randomUUID()
    val dataStore = mock[DataStore]
    val projection = new DataStoreIncomingFriendRequestsProjection(dataStore)
    val friendRequestSent = FriendRequestSent(senderId, userId)
    def processFriendRequest() = {
      projection.process(friendRequestSent)
    }

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
        having(contain(FriendRequestReceived(userId, senderId, friendRequestSent.id))))
    }

    processFriendRequest()
  }
}