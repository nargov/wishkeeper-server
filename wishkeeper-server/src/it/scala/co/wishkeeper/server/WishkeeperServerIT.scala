package co.wishkeeper.server

import java.util.UUID
import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.ActorMaterializer
import co.wishkeeper.DataStoreTestHelper
import co.wishkeeper.server.Commands.{ConnectFacebookUser, SendFriendRequest}
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAfterAll

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

class WishkeeperServerIT extends Specification with BeforeAfterAll {
  implicit val system = ActorSystem("WishkeeperServerIT")
  implicit val materializer = ActorMaterializer()
  private val threadPool = Executors.newCachedThreadPool()
  implicit val executionContext = ExecutionContext.fromExecutor(threadPool)

  val dataStoreTestHelper = DataStoreTestHelper()
  val facebookTestHelper = new FacebookTestHelper

  val server = new WishkeeperServer

  "User should be able to send a friend request" in { implicit ee: ExecutionEnv =>
    val testUsers = facebookTestHelper.createTestUsers(2, installApp = true)
    facebookTestHelper.makeFriends(testUsers.head, testUsers.tail)
    val connectRequests = testUsers.map(user => ConnectFacebookUser(user.id, user.access_token, UUID.randomUUID()))

    val eventualConnectResponseCodes = connectRequests.map { req =>
      Http().singleRequest(
        HttpRequest().
          withMethod(HttpMethods.POST).
          withUri(s"http://localhost:${WebApi.defaultPort}/users/connect/facebook").
          withEntity(ContentTypes.`application/json`, req.asJson.noSpaces)).
        map(_.status)
    }

    Await.result(Future.sequence(eventualConnectResponseCodes), 2.seconds).forall(_ == StatusCodes.OK) must beTrue

    val futurePotentialFriend = Http().singleRequest(
      HttpRequest().
        withUri(s"http://localhost:${WebApi.defaultPort}/users/friends/facebook").
        withHeaders(
          RawHeader(WebApi.sessionIdHeader, connectRequests.head.sessionId.toString),
          RawHeader(WebApi.facebookAccessTokenHeader, connectRequests.head.authToken))).
      flatMap(_.entity.dataBytes.runFold("")(_ + _.utf8String)).
      map(decode[List[PotentialFriend]]).
      map {
        case Right(potentialFriends) => potentialFriends.head
        case Left(e) => throw e
      }

    val friendId = Await.result(futurePotentialFriend, 2.seconds).userId
    friendId must beAnInstanceOf[UUID]

    val eventualFriendRequestCode =
      Http().singleRequest(
        HttpRequest().
          withUri(s"http://localhost:${WebApi.defaultPort}/users/friends/request").
          withMethod(HttpMethods.POST).
          withHeaders(
            RawHeader(WebApi.sessionIdHeader, connectRequests.head.sessionId.toString),
            RawHeader(WebApi.facebookAccessTokenHeader, connectRequests.head.authToken)).
          withEntity(ContentTypes.`application/json`, SendFriendRequest(friendId).asJson.noSpaces)).
        map(_.status)

    Await.result(eventualFriendRequestCode, 2.seconds) must beEqualTo(StatusCodes.OK)

    eventually {
      val eventualIncomingFriendRequests = Http().singleRequest(
        HttpRequest().
          withUri(s"http://localhost:${WebApi.defaultPort}/users/friends/requests/incoming").
          withHeaders(
            RawHeader(WebApi.sessionIdHeader, connectRequests.tail.head.sessionId.toString)
          )).
        flatMap(_.entity.dataBytes.runFold("")(_ + _.utf8String)).
        map(decode[List[UUID]]).
        map {
          case Right(friendRequests) => friendRequests
          case Left(e) => throw e
        }

      Await.result(eventualIncomingFriendRequests, 2.seconds) must have size 1
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
    system.terminate()
    threadPool.shutdown()
  }
}
