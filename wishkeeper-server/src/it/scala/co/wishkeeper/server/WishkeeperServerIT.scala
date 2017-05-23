package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.DataStoreTestHelper
import co.wishkeeper.server.Commands.{ConnectFacebookUser, SendFriendRequest, SetWishDetails}
import co.wishkeeper.server.HttpTestKit._
import io.circe.generic.auto._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAfterAll

import scala.concurrent.Future

class WishkeeperServerIT(implicit ee: ExecutionEnv) extends Specification with BeforeAfterAll with ResponseMatchers {

  val dataStoreTestHelper = DataStoreTestHelper()
  val facebookTestHelper = new FacebookTestHelper

  val server = new WishkeeperServer
  val usersEndpoint = s"http://localhost:${WebApi.defaultPort}/users"

  var testUsers: List[TestFacebookUser] = _

  "User should be able to send a friend request" in {
    val connectRequests: Seq[ConnectFacebookUser] = testUsers.map(user => ConnectFacebookUser(user.id, user.access_token, UUID.randomUUID()))
    val user1Connect :: user2Connect :: Nil = connectRequests
    Future.sequence(connectRequests.map(Post.async(s"$usersEndpoint/connect/facebook", _))) must forall(beOk).await

    val user1SessionHeader = WebApi.sessionIdHeader -> user1Connect.sessionId.toString
    val user2SessionHeader = WebApi.sessionIdHeader -> user2Connect.sessionId.toString
    val accessTokenHeader = WebApi.facebookAccessTokenHeader -> user1Connect.authToken

    val friends: List[PotentialFriend] = Get(s"$usersEndpoint/friends/facebook", Map(user1SessionHeader, accessTokenHeader)).to[List[PotentialFriend]]
    friends must have size 1
    friends.head.name must beEqualTo(testUsers(1).userProfile.get.name.get)

    Post(s"$usersEndpoint/friends/request", SendFriendRequest(friends.head.userId), Map(user1SessionHeader)) must beOk

    eventually {
      Get(s"$usersEndpoint/friends/requests/incoming", Map(user2SessionHeader)).to[List[UUID]] must have size 1
    }
  }

  "User should be able to save Wish details" in {
    val facebookUser = testUsers.head
    val sessionId = UUID.randomUUID()

    Post(s"$usersEndpoint/connect/facebook", ConnectFacebookUser(facebookUser.id, facebookUser.access_token, sessionId))

    val wish = Wish(UUID.randomUUID()).withName("Expected Name").withImageLink("expected image link")
    Post(s"$usersEndpoint/wishes", SetWishDetails(wish), Map(WebApi.sessionIdHeader -> sessionId.toString)) must beOk

    val userId = Get(s"http://localhost:${WebApi.defaultManagementPort}/users/facebook/${facebookUser.id}").to[UUID]

    val response = Get(s"http://localhost:${WebApi.defaultManagementPort}/users/$userId/wishes")
    response must beOk
    response.to[List[Wish]] must contain(exactly(wish))
  }

  override def beforeAll(): Unit = {
    CassandraDocker.start()
    dataStoreTestHelper.start()
    dataStoreTestHelper.createSchema()
    server.start()

    testUsers = facebookTestHelper.createTestUsers(2, installApp = true)
    facebookTestHelper.makeFriends(testUsers.head, testUsers.tail)
  }

  override def afterAll(): Unit = {
    facebookTestHelper.deleteTestUsers()
    dataStoreTestHelper.stop()
  }
}




