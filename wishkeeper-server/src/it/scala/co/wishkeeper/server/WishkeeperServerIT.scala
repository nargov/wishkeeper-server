package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.DataStoreTestHelper
import co.wishkeeper.server.Commands.{ConnectFacebookUser, SendFriendRequest}
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

  "User should be able to send a friend request" in {
    val testUsers = facebookTestHelper.createTestUsers(2, installApp = true)
    facebookTestHelper.makeFriends(testUsers.head, testUsers.tail)

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

  override def beforeAll(): Unit = {
    CassandraDocker.start()
    dataStoreTestHelper.start()
    dataStoreTestHelper.createSchema()
    server.start()
  }

  override def afterAll(): Unit = {
    facebookTestHelper.deleteTestUsers()
    dataStoreTestHelper.stop()
  }
}




