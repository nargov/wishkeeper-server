package co.wishkeeper.server

import java.util.UUID
import java.util.UUID.randomUUID

import co.wishkeeper.DataStoreTestHelper
import co.wishkeeper.json._
import co.wishkeeper.server.Commands.{ConnectFacebookUser, SendFriendRequest, SetWishDetails}
import co.wishkeeper.server.FriendRequestStatus.Pending
import co.wishkeeper.server.HttpTestKit._
import co.wishkeeper.server.projections.PotentialFriend
import co.wishkeeper.test.utils.WishMatchers
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.{BeforeAfter, Specification}
import org.specs2.specification.{BeforeAfterAll, Scope}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

class WishkeeperServerIT(implicit ee: ExecutionEnv) extends Specification with BeforeAfterAll with ResponseMatchers
  with WishMatchers with NotificationMatchers {
  sequential //TODO remove this when thread safe - see CommandProcessor FIXME for more details.

  implicit val circeConfig = Configuration.default.withDefaults

  val dataStoreTestHelper = DataStoreTestHelper()
  val facebookTestHelper = new FacebookTestHelper

  val server = new WishkeeperServer
  val usersEndpoint = s"http://localhost:${WebApi.defaultPort}/users"
  val usersManagementEndpoint = s"http://localhost:${WebApi.defaultManagementPort}/users"

  var testUsers: List[TestFacebookUser] = _

  trait Context extends Scope with BeforeAfter {
    override def after = {
      facebookTestHelper.deleteTestUsers()
    }

    override def before = {
      testUsers = facebookTestHelper.createTestUsers(2, installApp = true)
      facebookTestHelper.makeFriends(testUsers.head, testUsers.tail)
    }
  }

  "User should be able to send a friend request" in new Context {
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
      Get(s"$usersEndpoint/notifications", Map(user2SessionHeader)).to[List[Notification]] must contain(
        aNotificationWith(aFriendRequestNotificationWithStatus(Pending)))
    }
  }

  "User should be able to save Wish details" in new Context {
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

  "Upload a wish image" in new Context {
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
    maybeLinks.map(_.size) must beSome(4) //todo get the number of extensions from the production code after refactoring
    val maybeResponses = maybeLinks.map(_.map(imageLink => Get(imageLink.url)))
    maybeResponses must beSome(contain(beSuccessful))
  }

  "Get a notification when friend approves friend request" in new Context {
    val connectRequests: Seq[ConnectFacebookUser] = testUsers.map(user => ConnectFacebookUser(user.id, user.access_token, randomUUID()))
    val user1Connect :: user2Connect :: Nil = connectRequests
    Future.sequence(connectRequests.map(Post.async(s"$usersEndpoint/connect/facebook", _))) must forall(beOk).await(20, 0.5.seconds)

    val user1SessionHeader = WebApi.sessionIdHeader -> user1Connect.sessionId.toString
    val user2SessionHeader = WebApi.sessionIdHeader -> user2Connect.sessionId.toString
    val accessTokenHeader = WebApi.facebookAccessTokenHeader -> user1Connect.authToken

    val friends: List[PotentialFriend] = Get(s"$usersEndpoint/friends/facebook", Map(user1SessionHeader, accessTokenHeader)).to[List[PotentialFriend]]
    Post(s"$usersEndpoint/friends/request", SendFriendRequest(friends.head.userId), Map(user1SessionHeader)) must beOk

    val notifications = Get(s"$usersEndpoint/notifications", Map(user2SessionHeader)).to[List[Notification]]
    val friendReqId = notifications.head.data.asInstanceOf[FriendRequestNotification].requestId
    Post(s"$usersEndpoint/notifications/friendreq/$friendReqId/approve", (), Map(user2SessionHeader)) must beOk

    val user1Notifications = Get(s"$usersEndpoint/notifications", Map(user1SessionHeader)).to[List[Notification]]
    user1Notifications must contain(aNotificationType[FriendRequestAcceptedNotification])
  }

  override def beforeAll(): Unit = {
    CassandraDocker.start()
    dataStoreTestHelper.start()
    dataStoreTestHelper.createSchema()
    server.start()
  }

  override def afterAll(): Unit = {
    dataStoreTestHelper.stop()
  }
}
