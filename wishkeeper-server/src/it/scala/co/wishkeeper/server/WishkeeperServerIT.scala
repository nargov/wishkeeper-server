package co.wishkeeper.server

import java.util.UUID
import java.util.UUID.randomUUID

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest, WebSocketUpgradeResponse}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import co.wishkeeper.DataStoreTestHelper
import co.wishkeeper.json._
import co.wishkeeper.server.FriendRequestStatus.Pending
import co.wishkeeper.server.HttpTestKit._
import co.wishkeeper.server.NotificationsData.{FriendRequestAcceptedNotification, FriendRequestNotification}
import co.wishkeeper.server.projections.PotentialFriend
import co.wishkeeper.server.search.{SearchQuery, UserSearchResult, UserSearchResults}
import co.wishkeeper.server.user.commands.{ConnectFacebookUser, SendFriendRequest, SetFacebookUserInfo, SetWishDetails}
import co.wishkeeper.server.web.WebApi.sessionIdHeader
import co.wishkeeper.server.web.{WebApi, WebSocketsStats}
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
  val baseUrl = s"http://localhost:${WebApi.defaultPort}"
  val usersEndpoint = s"$baseUrl/users"
  val managementEndpoint = s"http://localhost:${WebApi.defaultManagementPort}"
  val usersManagementEndpoint = s"$managementEndpoint/users"

  var testUsers: List[TestFacebookUser] = _

  "User should be able to send a friend request" in new Context {
    val connectRequests: Seq[ConnectFacebookUser] = testUsers.map(user => ConnectFacebookUser(user.id, user.access_token, randomUUID(), user.email))
    val user1Connect :: user2Connect :: Nil = connectRequests
    Future.sequence(connectRequests.map(Post.async(s"$usersEndpoint/connect/facebook", _))) must forall(beOk).await(20, 0.5.seconds)

    val user1SessionHeader = sessionIdHeader -> user1Connect.sessionId.toString
    val user2SessionHeader = sessionIdHeader -> user2Connect.sessionId.toString
    val accessTokenHeader = WebApi.facebookAccessTokenHeader -> user1Connect.authToken

    val friends: List[PotentialFriend] = Get(s"$usersEndpoint/friends/facebook", Map(user1SessionHeader, accessTokenHeader)).to[List[PotentialFriend]]
    friends must have size 1
    friends.head.name must beEqualTo(testUsers(1).userProfile.get.name.get)

    Post(s"$usersEndpoint/friends/request", SendFriendRequest(friends.head.userId), Map(user1SessionHeader)) must beOk

    eventually {
      Get(s"$usersEndpoint/notifications", Map(user2SessionHeader)).to[UserNotifications].list must contain(
        aNotificationWith(aFriendRequestNotificationWithStatus(Pending)))
    }
  }

  "User should be able to save Wish details" in new Context {
    val facebookUser = testUsers.head
    val sessionId = randomUUID()

    Post(s"$usersEndpoint/connect/facebook", ConnectFacebookUser(facebookUser.id, facebookUser.access_token, sessionId, facebookUser.email))
    val userId = Get(s"$usersManagementEndpoint/facebook/${facebookUser.id}").to[UUID]

    val wish = Wish(userId).withName("Expected Name")
    Post(s"$usersEndpoint/wishes", SetWishDetails(wish), Map(sessionIdHeader -> sessionId.toString)) must beOk

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

    Post(s"$usersEndpoint/connect/facebook", ConnectFacebookUser(facebookUser.id, facebookUser.access_token, sessionId, facebookUser.email))

    ImagePost(s"$usersEndpoint/wishes/$wishId/image", testImage, imageId, Map(
      sessionIdHeader -> sessionId.toString,
      WebApi.imageDimensionsHeader -> s"${testImage.width},${testImage.height}"
    )) must beSuccessful

    val userWishes = Get(s"$usersEndpoint/wishes", Map(sessionIdHeader -> sessionId.toString)).to[UserWishes]

    val maybeWish = userWishes.wishes.find(_.id == wishId)
    val maybeLinks: Option[List[ImageLink]] = maybeWish.flatMap(_.image).map(_.links)
    maybeLinks.map(_.size) must beSome(4) //todo get the number of extensions from the production code after refactoring
    val maybeResponses = maybeLinks.map(_.map(imageLink => Get(imageLink.url)))
    maybeResponses.map(_.map(_.response.entity.discardBytes()))
    maybeResponses must beSome(contain(beSuccessful))
  }

  "Get a notification when friend approves friend request" in new Context {
    val connectRequests: Seq[ConnectFacebookUser] = testUsers.map(user => ConnectFacebookUser(user.id, user.access_token, randomUUID(), user.email))
    val user1Connect :: user2Connect :: Nil = connectRequests
    Future.sequence(connectRequests.map(Post.async(s"$usersEndpoint/connect/facebook", _))) must forall(beOk).await(20, 0.5.seconds)

    val user1SessionHeader = sessionIdHeader -> user1Connect.sessionId.toString
    val user2SessionHeader = sessionIdHeader -> user2Connect.sessionId.toString
    val accessTokenHeader = WebApi.facebookAccessTokenHeader -> user1Connect.authToken

    val friends: List[PotentialFriend] = Get(s"$usersEndpoint/friends/facebook", Map(user1SessionHeader, accessTokenHeader)).to[List[PotentialFriend]]
    Post(s"$usersEndpoint/friends/request", SendFriendRequest(friends.head.userId), Map(user1SessionHeader)) must beOk

    val notifications = Get(s"$usersEndpoint/notifications", Map(user2SessionHeader)).to[UserNotifications].list
    val friendReqId = notifications.head.data.asInstanceOf[FriendRequestNotification].requestId
    Post(s"$usersEndpoint/notifications/friendreq/$friendReqId/approve", (), Map(user2SessionHeader)) must beOk

    val user1Notifications = Get(s"$usersEndpoint/notifications", Map(user1SessionHeader)).to[UserNotifications]
    user1Notifications.list must contain(aNotificationType[FriendRequestAcceptedNotification])
  }

  "receive notification through web socket" in new Context {
    val connectRequests: Seq[ConnectFacebookUser] = testUsers.map(user => ConnectFacebookUser(user.id, user.access_token, randomUUID(), user.email))
    val user1Connect :: user2Connect :: Nil = connectRequests
    Future.sequence(connectRequests.map(Post.async(s"$usersEndpoint/connect/facebook", _))) must forall(beOk).await(20, 0.5.seconds)
    val user1SessionId: String = user1Connect.sessionId.toString
    val user2SessionId: String = user2Connect.sessionId.toString

    val webSocketRequest = WebSocketRequest(s"ws://localhost:12300/ws?wsid=$user2SessionId")
    val wsFlow: Flow[Message, Message, Future[WebSocketUpgradeResponse]] = Http().webSocketClientFlow(webSocketRequest)
    val incoming = Sink.queue[Message]()
    val outgoing = Source.single(TextMessage("Hi"))
    val (upgradeResponse, queue) = outgoing
      .viaMat(wsFlow)(Keep.right)
      .toMat(incoming)(Keep.both)
      .run()

    val connected = upgradeResponse.map(_.response.status == StatusCodes.SwitchingProtocols)
    connected must beTrue.await

    Get(s"$managementEndpoint/stats/ws").to[WebSocketsStats].connections must beEqualTo(1).eventually

    val user2Id = Get(s"$baseUrl/me/id", Map(sessionIdHeader -> user2Connect.sessionId.toString)).to[UUID]
    Post(s"$usersEndpoint/friends/request", SendFriendRequest(user2Id), Map(sessionIdHeader -> user1SessionId)) must beOk

    queue.pull() must beSome[Message].await
  }

  "Search for a user by name" in new Context {
    val connectRequests: Seq[ConnectFacebookUser] = testUsers.map(user => ConnectFacebookUser(user.id, user.access_token, randomUUID(), user.email))
    val user1Connect :: user2Connect :: Nil = connectRequests
    Future.sequence(connectRequests.map(Post.async(s"$usersEndpoint/connect/facebook", _))) must forall(beOk).await(20, 0.5.seconds)
    val user1SessionId: String = user1Connect.sessionId.toString

    val profile: UserProfile = testUsers.head.userProfile.get
    val secondUserProfile = testUsers.tail.head.userProfile.get
    val name = profile.name.get
    val (firstName, lastName) = name.splitAt(name.indexOf(" "))
    val testUserNamePrefix: String = firstName.take(3)
    val headers = Map(sessionIdHeader -> user1SessionId)
    val userId = Get(s"$baseUrl/me/id", headers).to[UUID]

    Post(s"$baseUrl/users/profile/facebook", SetFacebookUserInfo(name = profile.name, picture = profile.picture), headers)
    Post(s"$baseUrl/users/profile/facebook", SetFacebookUserInfo(name = secondUserProfile.name, picture = secondUserProfile.picture),
      Map(sessionIdHeader -> user2Connect.sessionId.toString))

    val results: UserSearchResults = Post(s"$baseUrl/search/user", SearchQuery(testUserNamePrefix), headers).to[UserSearchResults]
    results.users must contain(UserSearchResult(userId, name, profile.picture))
  }

  trait Context extends Scope with BeforeAfter {
    override def after = {
      facebookTestHelper.deleteTestUsers()
    }

    override def before = {
      testUsers = facebookTestHelper.createTestUsers(2, installApp = true)
      facebookTestHelper.makeFriends(testUsers.head, testUsers.tail)
    }
  }

  override def beforeAll(): Unit = {
    println("***************************************")
    println("*****  WishkeeperServerIT Starts  *****")
    println("***************************************")

    CassandraDocker.start()
    dataStoreTestHelper.start()
    dataStoreTestHelper.createSchema()
    server.start()
  }

  override def afterAll(): Unit = {
    dataStoreTestHelper.stop()
    server.stop()
    println("***************************************")
    println("******  WishkeeperServerIT Ends  (*****")
    println("***************************************")
  }
}