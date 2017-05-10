package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.Events.{FriendRequestReceived, FriendRequestSent}
import com.wixpress.common.specs2.JMock
import org.joda.time.DateTime
import org.specs2.mutable.Specification

class DataStoreIncomingFriendRequestsProjectionTest extends Specification with JMock {
  "save incoming friend request" in {
    val userId = UUID.randomUUID()
    val senderId = UUID.randomUUID()
    val dataStore = mock[DataStore]
    val projection = new DataStoreIncomingFriendRequestsProjection(dataStore)

    checking {
      allowing(dataStore).lastSequenceNum(userId).willReturn(Some(5L))
      oneOf(dataStore).saveUserEvents(
        having(equalTo(userId)),
        having(any[Option[Long]]),
        having(any[DateTime]),
        having(===(FriendRequestReceived(userId, senderId) :: Nil)))
    }

    projection.process(FriendRequestSent(senderId, userId))
  }
}
