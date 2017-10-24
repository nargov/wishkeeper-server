package co.wishkeeper.server.projections

import java.util.UUID.randomUUID

import co.wishkeeper.server.DataStore
import co.wishkeeper.server.Events.FriendRequestStatusChanged
import co.wishkeeper.server.FriendRequestStatus.Approved
import com.wixpress.common.specs2.JMock
import org.joda.time.DateTime
import org.specs2.mutable.Specification

class DataStoreFriendRequestsProjectionTest extends Specification with JMock {
  "return a FriendRequestStatusChanged when friend accepted" in {
    val userId = randomUUID()
    val friendId = randomUUID()
    val requestId = randomUUID()
    val dataStore = mock[DataStore]
    val notificationsProjection = new DataStoreFriendRequestsProjection(dataStore)

    checking {
      allowing(dataStore).lastSequenceNum(friendId).willReturn(Some(5L))
      oneOf(dataStore).saveUserEvents(having(===(friendId)), having(any[Option[Long]]), having(any[DateTime]), having(contain(
        FriendRequestStatusChanged(friendId, requestId, friendId, Approved)
      ))).willReturn(true)
    }

    notificationsProjection.process(FriendRequestStatusChanged(userId, requestId, friendId, Approved))
  }
}
