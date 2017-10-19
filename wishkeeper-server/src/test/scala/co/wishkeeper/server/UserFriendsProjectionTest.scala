package co.wishkeeper.server

import java.util.UUID
import java.util.UUID.randomUUID

import co.wishkeeper.server.Events._
import co.wishkeeper.server.EventsTestHelper.{asEventInstants, userConnectEvent}
import co.wishkeeper.server.FriendRequestStatus.Approved
import co.wishkeeper.server.projections._
import com.wixpress.common.specs2.JMock
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.Matcher
import org.specs2.mutable.Specification

import scala.concurrent.Future

class UserFriendsProjectionTest(implicit ee: ExecutionEnv) extends Specification with JMock {

  "return a list of potential friends that are wishkeeper users" in {
    val userFacebookId = "user-facebook-id"
    val accessToken = "access-token"

    val facebookConnector = mock[FacebookConnector]
    val userIdByFacebookIdProjection = mock[UserIdByFacebookIdProjection]
    val dataStore = mock[DataStore]
    val userFriendsProjection: UserFriendsProjection = new SimpleUserFriendsProjection(facebookConnector, userIdByFacebookIdProjection, dataStore)

    val expectedUserName = "Expected Wishkeeper Friend"
    val facebookFriends = List(
      FacebookFriend(expectedUserName, "expected-friend-id"),
      FacebookFriend("Expected non-Wishkeeper Friend", "some-id")
    )
    val expectedFriendUserId = randomUUID()

    checking {
      ignoring(dataStore)
      allowing(facebookConnector).friendsFor(userFacebookId, accessToken).willReturn(
        Future.successful(facebookFriends))
      allowing(userIdByFacebookIdProjection).get(facebookFriends.map(_.id)).willReturn(Map(facebookFriends.head.id -> expectedFriendUserId))
    }
    userFriendsProjection.potentialFacebookFriends(userFacebookId, accessToken) must contain(
      aPotentialFriendWith(id = expectedFriendUserId, name = expectedUserName)).await
  }

  "return a list of current friends" in {
    val userId = randomUUID()
    val dataStore = mock[DataStore]
    val friendId = randomUUID()
    val image = "Joe's image"
    val name = "Joe"
    val userFriends = UserFriends(Friend(friendId, Some(name), Some(image)) :: Nil)
    val requestId = randomUUID()

    checking {
      allowing(dataStore).userEvents(userId).willReturn(asEventInstants(List(
        userConnectEvent(userId),
        FriendRequestReceived(userId, friendId, Option(requestId)),
        FriendRequestStatusChanged(userId, requestId, friendId, Approved)
      )))
      allowing(dataStore).userEvents(friendId).willReturn(asEventInstants(List(
        userConnectEvent(friendId),
        FriendRequestSent(friendId, userId, Option(requestId)),
        FriendRequestStatusChanged(userId, requestId, friendId, Approved),
        UserPictureSet(friendId, image),
        UserNameSet(friendId, name)
      )))
    }

    new SimpleUserFriendsProjection(null, null, dataStore).friendsFor(userId) must beEqualTo(userFriends)
  }

  def aPotentialFriendWith(id: UUID, name: String): Matcher[PotentialFriend] =
    (===(name) ^^ {(_:PotentialFriend).name}) and (===(id) ^^ {(_:PotentialFriend).userId})
}
