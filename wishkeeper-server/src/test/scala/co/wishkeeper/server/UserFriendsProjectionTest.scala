package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.projections.{DelegatingUserFriendsProjection, UserFriendsProjection, UserIdByFacebookIdProjection}
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
    val userFriendsProjection: UserFriendsProjection = new DelegatingUserFriendsProjection(facebookConnector, userIdByFacebookIdProjection)

    val expectedUserName = "Expected Wishkeeper Friend"
    val facebookFriends = List(
      FacebookFriend(expectedUserName, "expected-friend-id"),
      FacebookFriend("Expected non-Wishkeeper Friend", "some-id")
    )
    val expectedFriendUserId = UUID.randomUUID()

    checking {
      allowing(facebookConnector).friendsFor(userFacebookId, accessToken).willReturn(
        Future.successful(facebookFriends))
      allowing(userIdByFacebookIdProjection).get(facebookFriends.map(_.id)).willReturn(Map(facebookFriends.head.id -> expectedFriendUserId))
    }

    userFriendsProjection.potentialFacebookFriends(userFacebookId, accessToken) must contain(
      aPotentialFriendWith(id = expectedFriendUserId, name = expectedUserName)).await
  }

  def aPotentialFriendWith(id: UUID, name: String): Matcher[PotentialFriend] =
    (===(name) ^^ {(_:PotentialFriend).name}) and (===(id) ^^ {(_:PotentialFriend).userId})
}

