package co.wishkeeper.server

import java.util.concurrent.atomic.AtomicReference
import java.util.function.UnaryOperator

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods.POST
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, StatusCodes}
import akka.stream.ActorMaterializer
import co.wishkeeper.server.FacebookTestHelper._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import io.circe.parser.decode

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Success


class FacebookTestHelper(implicit ec: ExecutionContext, actorSystem: ActorSystem, materializer: ActorMaterializer) {

  implicit val circeConfig = Configuration.default.withDefaults

  private val testFbUsers = new AtomicReference[Map[String, TestFacebookUser]](Map.empty)

  def makeFriends(testUser: TestFacebookUser, friends: List[TestFacebookUser]): Unit = {
    val eventualFriends = friends.map { friend =>
      friendRequestFor(testUser, friend).flatMap {
        case res if res.status == StatusCodes.OK => friendRequestFor(friend, testUser)
      }
    }

    val results = Await.result(Future.sequence(eventualFriends), 20.seconds * friends.size)
    if (results.exists(_.status != StatusCodes.OK)) throw new IllegalStateException("Failed to create relationships")
  }

  private def friendRequestFor(testUser: TestFacebookUser, friend: TestFacebookUser): Future[HttpResponse] = {
    Http().singleRequest(HttpRequest().
      withUri(s"https://graph.facebook.com/v2.9/${testUser.id}/friends/${friend.id}?access_token=${testUser.access_token}").
      withMethod(POST))
  }

  def addAccessToken(user: TestFacebookUser): Future[TestFacebookUser] = addAccessTokens(user :: Nil).map(_.head)

  def addAccessTokens(users: List[TestFacebookUser]): Future[List[TestFacebookUser]] = {
    val eventualResponse = Http().singleRequest(HttpRequest().
      withUri(s"https://graph.facebook.com/v2.9/$wishkeeperFacebookTestAppId/accounts/test-users?access_token=$access_token"))
    val eventualJson: Future[String] = eventualResponse.flatMap(_.entity.dataBytes.runFold("")(_ + _.utf8String))
    val eventualUsersAccessDataList = eventualJson.map(json => decode[TestUsersAccessDataList](json)).map {
      case Right(list) => list
      case Left(err) => throw err
    }
    val tokensMap = eventualUsersAccessDataList.map(list => list.data.map(_.id).zip(list.data).toMap)
    tokensMap.map(tokens => users.map(user => user.copy(access_token = tokens(user.id).access_token)))
  }

  def createTestUser(installApp: Boolean = false): TestFacebookUser = createTestUsers(1, installApp).head

  def createTestUsers(numUsers: Int = 1, installApp: Boolean = false): List[TestFacebookUser] = {
    println(s"Creating $numUsers test user${if (numUsers > 1) "s" else ""}")
    val eventualUsers = Future.sequence((1 to numUsers).toList.map { _ =>
      val eventualTestUserResponse = Http().singleRequest(HttpRequest().
        withMethod(POST).
        withUri(s"https://graph.facebook.com/v2.9/$wishkeeperFacebookTestAppId/accounts/test-users").
        withEntity(s"access_token=$access_token&installed=$installApp"))
      val eventualJson: Future[String] = eventualTestUserResponse.flatMap(_.entity.dataBytes.runFold("")(_ + _.utf8String))
      val eventualUser = eventualJson.map(json => decode[TestFacebookUser](json)).map {
        case Right(user) => user
        case Left(err) => throw err
      }
      eventualUser.map { user =>
        testFbUsers.updateAndGet(new UnaryOperator[Map[String, TestFacebookUser]] {
          override def apply(t: Map[String, TestFacebookUser]): Map[String, TestFacebookUser] = t + (user.id -> user)
        })
        user
      }
    })
    val users: List[TestFacebookUser] = Await.result(eventualUsers, 10.seconds * numUsers)

    println(s"Created $numUsers test user${if (numUsers > 1) "s" else ""}")

    if (installApp) addUserDetails(users) else users
  }

  def addUserDetails(user: TestFacebookUser): TestFacebookUser = addUserDetails(user :: Nil).head

  def addUserDetails(users: List[TestFacebookUser]): List[TestFacebookUser] = {
    val eventualUsersWithProfile = users.map { user =>
      val eventualResponse = Http().singleRequest(HttpRequest().
        withUri(s"https://graph.facebook.com/v2.9/${user.id}?access_token=${user.access_token}&fields=name,birthday,email,gender,locale"))
      val testUserDetails = eventualResponse.flatMap(_.entity.dataBytes.runFold("")(_ + _.utf8String))
      val testUserProfile = testUserDetails.map(decode[UserProfile]).map {
        case Right(profile) => profile
        case Left(err) => throw err
      }
      testUserProfile.map(profile => user.copy(userProfile = Some(profile)))
    }
    Await.result(Future.sequence(eventualUsersWithProfile), 30.seconds * users.size)
  }

  def deleteTestUsers(): Unit = {
    println("Deleting test users...")
    val requests = testFbUsers.get().values.map { user =>
      Http().singleRequest(
        HttpRequest().withMethod(HttpMethods.DELETE).withUri(s"https://graph.facebook.com/v2.9/${user.id}").
          withEntity(s"access_token=$access_token")
      ).andThen {
        case Success(res) if res.status == StatusCodes.OK =>
          testFbUsers.getAndUpdate(new UnaryOperator[Map[String, TestFacebookUser]] {
            override def apply(t: Map[String, TestFacebookUser]): Map[String, TestFacebookUser] = t - user.id
          })
      }
    }
    Await.ready(Future.sequence(requests), 10.seconds * testFbUsers.get().size)
    println("Test users deleted.")
  }
}

object FacebookTestHelper {
  val wishkeeperFacebookTestAppId = "1376924702342472"
  val wishkeeperFacebookTestAppSecret = "3f5ee9ef27bd152217246ab02bed5725"
  val access_token = s"$wishkeeperFacebookTestAppId|$wishkeeperFacebookTestAppSecret"
}

case class TestUsersAccessDataList(data: List[TestUserAccessToken])

case class TestUserAccessToken(id: String, access_token: String)

case class TestFacebookUser(id: String, email: String, password: String, access_token: String = "", userProfile: Option[UserProfile] = None)
