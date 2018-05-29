package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.Events._
import co.wishkeeper.server.messaging.NotificationsUpdated
import co.wishkeeper.server.projections.DataStoreIncomingFriendRequestsProjection
import com.wixpress.common.specs2.JMock
import org.joda.time.DateTime
import org.specs2.matcher.JsonMatchers
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class DataStoreIncomingFriendRequestsProjectionTest extends Specification with JMock with JsonMatchers {

  trait Context extends Scope {
    val userId = UUID.randomUUID()
    val senderId = UUID.randomUUID()
    val dataStore = mock[DataStore]
    val sendMessageSpy = mock[(String, UUID) => Unit]
    val projection = new DataStoreIncomingFriendRequestsProjection(dataStore, sendMessageSpy)
    val requestId: UUID = UUID.randomUUID()
    val friendRequestSent = FriendRequestSent(senderId, userId, Option(requestId))

    def assumeExistingEvents() = checking {
      allowing(dataStore).lastSequenceNum(userId).willReturn(Some(5L))
    }
  }

  "save incoming friend request" in new Context {
    assumeExistingEvents()
    checking {
      ignoring(sendMessageSpy)
      oneOf(dataStore).saveUserEvents(
        having(equalTo(userId)),
        having(any[Option[Long]]),
        having(any[DateTime]),
        having(contain(FriendRequestReceived(userId, senderId, Option(requestId))))).willReturn(true)
    }

    projection.process(friendRequestSent)
  }

  "notify user of incoming friend request" in new Context {
    assumeExistingEvents()
    checking {
      allowing(dataStore).saveUserEvents(having(any), having(any), having(any), having(any)).willReturn(true)
      oneOf(sendMessageSpy).apply(having(/("type" -> "NotificationsUpdated")), having(===(userId)))
    }

    projection.process(friendRequestSent)
  }
}