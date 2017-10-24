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
import org.specs2.specification.Scope

import scala.concurrent.Future

class UserFriendsProjectionTest(implicit ee: ExecutionEnv) extends Specification with JMock {

  "return a list of potential friends that are wishkeeper users" in new Context {
    assumingExistingFacebookFriends()
    checking {
      allowing(dataStore).userEvents(userId).willReturn(asEventInstants(List(
        userConnectEvent(userId),
        UserFacebookIdSet(userId, userFacebookId)
      )))
    }

    userFriendsProjection.potentialFacebookFriends(userId, accessToken) must contain(
      aPotentialFriendWith(id = friendId, name = expectedUserName)).await
  }

  "return a list of current friends" in new Context {
    val userFriends = UserFriends(Friend(friendId, Some(name), Some(image)) :: Nil)

    assumingExistingFriend()

    userFriendsProjection.friendsFor(userId) must beEqualTo(userFriends)
  }

  "not return user as potential friend if already friend" in new Context {
    assumingExistingFacebookFriends()
    assumingExistingFriend()

    userFriendsProjection.potentialFacebookFriends(userId, accessToken) must beEmpty[List[PotentialFriend]].await
  }

  "not return user as potential friend if friend request sent" in new Context {
    assumingExistingFacebookFriends()
    checking {
      allowing(dataStore).userEvents(userId).willReturn(asEventInstants(List(
        userConnectEvent(userId),
        UserFacebookIdSet(userId, userFacebookId),
        FriendRequestSent(userId, friendId, Option(requestId))
      )))
    }

    userFriendsProjection.potentialFacebookFriends(userId, accessToken) must beEmpty[List[PotentialFriend]].await
  }

  "not return user as potential friend if friend request received" in new Context {
    assumingExistingFacebookFriends()
    checking {
      allowing(dataStore).userEvents(userId).willReturn(asEventInstants(List(
        userConnectEvent(userId),
        UserFacebookIdSet(userId, userFacebookId),
        FriendRequestReceived(userId, friendId, Option(requestId))
      )))
    }

    userFriendsProjection.potentialFacebookFriends(userId, accessToken) must beEmpty[List[PotentialFriend]].await
  }


  trait Context extends Scope{
    val userFacebookId = "user-facebook-id"
    val accessToken = "access-token"
    val userId = randomUUID()
    val image = "Joe's image"
    val name = "Joe"
    val requestId = randomUUID()


    val facebookConnector = mock[FacebookConnector]
    val userIdByFacebookIdProjection = mock[UserIdByFacebookIdProjection]
    val dataStore = mock[DataStore]
    val userFriendsProjection: UserFriendsProjection = new SimpleUserFriendsProjection(facebookConnector, userIdByFacebookIdProjection, dataStore)
    val expectedUserName = "Expected Wishkeeper Friend"
    val facebookFriends = List(
      FacebookFriend(expectedUserName, "expected-friend-id"),
      FacebookFriend("Expected non-Wishkeeper Friend", "some-id")
    )
    val friendId = randomUUID()

    def ignoringDataStore() = checking {
      ignoring(dataStore)
    }

    def assumingExistingFacebookFriends() = checking {
      allowing(facebookConnector).friendsFor(userFacebookId, accessToken).willReturn(Future.successful(facebookFriends))
      allowing(userIdByFacebookIdProjection).get(facebookFriends.map(_.id)).willReturn(Map(facebookFriends.head.id -> friendId))
    }

    def assumingExistingFriend() = {
      checking {
        allowing(dataStore).userEvents(userId).willReturn(asEventInstants(List(
          userConnectEvent(userId),
          UserFacebookIdSet(userId, userFacebookId),
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
    }
  }

  def aPotentialFriendWith(id: UUID, name: String): Matcher[PotentialFriend] =
    (===(name) ^^ {(_:PotentialFriend).name}) and (===(id) ^^ {(_:PotentialFriend).userId})
}
