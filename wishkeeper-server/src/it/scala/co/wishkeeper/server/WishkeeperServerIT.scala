package co.wishkeeper.server

import java.util.UUID
import java.util.UUID.randomUUID

import akka.http.scaladsl.model.StatusCode
import co.wishkeeper.DataStoreTestHelper
import co.wishkeeper.json._
import co.wishkeeper.server.Commands.{ConnectFacebookUser, SendFriendRequest, SetWishDetails}
import co.wishkeeper.server.HttpTestKit._
import co.wishkeeper.server.projections.PotentialFriend
import io.circe.generic.auto._
import org.joda.time.DateTime
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.{AdaptableMatcher, MustThrownMatchers}
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAfterAll

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

class WishkeeperServerIT(implicit ee: ExecutionEnv) extends Specification with BeforeAfterAll with ResponseMatchers with WishMatchers {
  sequential //TODO remove this when thread safe - see CommandProcessor FIXME for more details.

  val dataStoreTestHelper = DataStoreTestHelper()
  val facebookTestHelper = new FacebookTestHelper

  val server = new WishkeeperServer
  val usersEndpoint = s"http://localhost:${WebApi.defaultPort}/users"
  val usersManagementEndpoint = s"http://localhost:${WebApi.defaultManagementPort}/users"

  var testUsers: List[TestFacebookUser] = _

  "User should be able to send a friend request" in {
    val connectRequests: Seq[ConnectFacebookUser] = testUsers.map(user => ConnectFacebookUser(user.id, user.access_token, randomUUID()))
    val user1Connect :: user2Connect :: Nil = connectRequests
    Future.sequence(connectRequests.map(Post.async(s"$usersEndpoint/connect/facebook", _))) must forall(beOk).await(20, 0.5.seconds)

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
    val sessionId = randomUUID()

    Post(s"$usersEndpoint/connect/facebook", ConnectFacebookUser(facebookUser.id, facebookUser.access_token, sessionId))
    val userId = Get(s"$usersManagementEndpoint/facebook/${facebookUser.id}").to[UUID]

    val wish = Wish(userId).withName("Expected Name")
    Post(s"$usersEndpoint/wishes", SetWishDetails(wish), Map(WebApi.sessionIdHeader -> sessionId.toString)) must beOk

    val response = Get(s"$usersManagementEndpoint/$userId/wishes")
    response must beOk
    response.to[List[Wish]] must contain(beEqualToIgnoringDates(wish.withCreator(userId)))
  }

  "Upload a wish image" in {
    val facebookUser = testUsers.head
    val sessionId = randomUUID()
    val testImage = TestImage.large()
    val wishId = randomUUID()
    val imageId = new UUID(0, 0)

    Post(s"$usersEndpoint/connect/facebook", ConnectFacebookUser(facebookUser.id, facebookUser.access_token, sessionId))

    ImagePost(s"$usersEndpoint/wishes/$wishId/image", testImage, imageId, Map(
      WebApi.sessionIdHeader -> sessionId.toString,
      WebApi.imageDimensionsHeader -> s"${testImage.width},${testImage.height}"
    )) must beSuccessful

    val userWishes = Get(s"$usersEndpoint/wishes", Map(WebApi.sessionIdHeader -> sessionId.toString)).to[UserWishes]

    val maybeWish = userWishes.wishes.find(_.id == wishId)
    val maybeLinks: Option[List[ImageLink]] = maybeWish.flatMap(_.image).map(_.links)
    maybeLinks.map(_.size) must beSome(3)
    val maybeResponses = maybeLinks.map(_.map(imageLink => Get(imageLink.url)))
    maybeResponses must beSome(contain(beSuccessful))
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


trait WishMatchers extends MustThrownMatchers {
  def beEqualToIgnoringDates: (Wish) => AdaptableMatcher[Wish] = ===(_:Wish) ^^^ ((_:Wish).copy(creationTime = new DateTime(0)))
}
object WishMatchers extends WishMatchers

