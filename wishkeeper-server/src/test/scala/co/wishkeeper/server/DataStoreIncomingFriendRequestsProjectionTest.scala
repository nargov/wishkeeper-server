package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.Commands.ConnectFacebookUser
import co.wishkeeper.server.Events.{FriendRequestReceived, FriendRequestSent, UserConnected}
import com.wixpress.common.specs2.JMock
import org.joda.time.DateTime
import org.specs2.mutable.Specification

import scala.util.Random

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

  "return existing incoming friend requests" in {
    val dataStore = mock[DataStore]
    val projection = new DataStoreIncomingFriendRequestsProjection(dataStore)

    val userId = UUID.randomUUID()
    val senderId = UUID.randomUUID()

    checking {
      allowing(dataStore).userEventsFor(userId).willReturn(List(
        EventsTestHelper.randomSession(userId),
        FriendRequestReceived(userId, senderId)
      ))
    }

    projection.awaitingApproval(userId) must beEqualTo(senderId :: Nil)
  }
}

object EventsTestHelper {

  def randomSession(userId: UUID) = UserConnected(userId, DateTime.now(), UUID.randomUUID())

  def randomString = Random.alphanumeric.take(12).mkString
}