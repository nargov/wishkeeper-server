package co.wishkeeper.server

import java.util.concurrent.atomic.AtomicReference
import java.util.function.UnaryOperator

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods.POST
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, StatusCodes}
import akka.stream.ActorMaterializer
import co.wishkeeper.server.FacebookTestHelper._
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Success


case class TestFacebookUser(id: String, email: String, password: String, accessToken: String = "", userProfile: UserProfile = UserProfile())


class FacebookTestHelper(implicit ec: ExecutionContext, actorSystem: ActorSystem, materializer: ActorMaterializer) {

  private val testFbUsers = new AtomicReference[Map[String, TestFacebookUser]](Map.empty)

  def makeFriends(testUser1: TestFacebookUser, testUser2: TestFacebookUser): Unit = {
    val eventualResponse: Future[HttpResponse] = Http().singleRequest(HttpRequest().
      withUri(s"https://graph.facebook.com/v2.9/${testUser1.id}/friends/${testUser2.id}?access_token=${testUser1.accessToken}").
      withMethod(POST)) andThen {
      case Success(res) if res.status == StatusCodes.OK =>
        Http().singleRequest(HttpRequest().
          withUri(s"https://graph.facebook.com/v2.9/${testUser2.id}/friends/${testUser1.id}?access_token=${testUser2.accessToken}").
          withMethod(POST))
    }
    val response = Await.result(eventualResponse, 30.seconds)
    if (response.status != StatusCodes.OK) throw new IllegalStateException("Couldn't make users friends - " +
      Await.result(response.entity.dataBytes.runReduce(_ ++ _).map(_.utf8String), 10.seconds))
  }

  def addAccessTokens(users: List[TestFacebookUser]): Future[List[TestFacebookUser]] = {
    val eventualTestUsers = Http().singleRequest(HttpRequest().
      withUri(s"https://graph.facebook.com/v2.9/$wishkeeperFacebookTestAppId/accounts/test-users?access_token=$access_token"))
    val eventualTestUsersJson: Future[String] = eventualTestUsers.flatMap(_.entity.dataBytes.runFold("")(_ + _.utf8String))
    val testUsers = eventualTestUsersJson.map(json => {
      println(json)
      parse(json).right.getOrElse(Json.Null)
    })
    val testUsersCursor = testUsers.map(_.hcursor)
    testUsersCursor.map { cursor =>
      users.map(user => {
        val token = cursor.downField("data").downAt(_.hcursor.get[String]("id") match {
          case Right(id) => id == user.id
          case Left(df) => throw df
        }).get[String]("access_token") match {
          case Right(str) => str
          case Left(df) => throw df
        }
        user.copy(accessToken = token)
      })
    }
  }

  def createTestUsers(numUsers: Int = 1, installApp: Boolean = false): List[TestFacebookUser] = {
    println(s"Creating $numUsers test user${if (numUsers > 1) "s" else ""}")
    val eventualUsers = Future.sequence((1 to numUsers).toList.map { _ =>
      val eventualTestUserResponse = Http().singleRequest(HttpRequest().
        withMethod(POST).
        withUri(s"https://graph.facebook.com/v2.9/$wishkeeperFacebookTestAppId/accounts/test-users").
        withEntity(s"access_token=$access_token&installed=$installApp"))
      val eventualJson: Future[String] = eventualTestUserResponse.flatMap(_.entity.dataBytes.runFold("")(_ + _.utf8String))
      val doc: Future[Json] = eventualJson.map(parse(_).right.getOrElse(Json.Null))
      val cursor = doc.map(_.hcursor)
      val testUserId: HCursor => String = extractTestUserProperty(_, "id")
      val testUserEmail: HCursor => String = extractTestUserProperty(_, "email")
      val testUserPassword: HCursor => String = extractTestUserProperty(_, "password")
      val eventualUser = cursor.map(c => TestFacebookUser(testUserId(c), testUserEmail(c), testUserPassword(c)))
      eventualUser.map { user =>
        testFbUsers.updateAndGet(new UnaryOperator[Map[String, TestFacebookUser]] {
          override def apply(t: Map[String, TestFacebookUser]): Map[String, TestFacebookUser] = t + (user.id -> user)
        })
        user
      }
    })
    val users: List[TestFacebookUser] = Await.result(eventualUsers, 30.seconds)

    val idAndUserTuples = users.map(_.id).zip(users)
    testFbUsers.getAndUpdate(new UnaryOperator[Map[String, TestFacebookUser]] {
      override def apply(t: Map[String, TestFacebookUser]): Map[String, TestFacebookUser] = t ++ idAndUserTuples
    })

    if (installApp) addUserDetails(users) else users
  }

  def addUserDetails(user: TestFacebookUser): TestFacebookUser = addUserDetails(user :: Nil).head

  def addUserDetails(users: List[TestFacebookUser]): List[TestFacebookUser] = {
    val eventualUsersWithAccessTokens: Future[List[TestFacebookUser]] = addAccessTokens(users)
    val usersWithAccessTokens = Await.result(eventualUsersWithAccessTokens, 20.seconds)

    usersWithAccessTokens.map { user =>
      val testUserDetailsResponse = Await.result(Http().singleRequest(HttpRequest().
        withUri(s"https://graph.facebook.com/v2.9/${user.id}?access_token=${user.accessToken}&fields=name,birthday,email,gender,locale")), 10.seconds)
      val testUserDetails = Await.result(testUserDetailsResponse.entity.dataBytes.runFold("")(_ + _.utf8String), 10.seconds)
      val testUserProfile = decode[UserProfile](testUserDetails).right.get
      user.copy(userProfile = testUserProfile)
    }
  }

  private def extractTestUserProperty(cursor: HCursor, property: String): String = {
    cursor.get[String](property) match {
      case Right(value) => value
      case Left(failure) => throw new IllegalStateException("Failed to create facebook test user: " + failure.message)
    }
  }

  def deleteTestUsers(): Unit = {
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
    Await.ready(Future.sequence(requests), 30.seconds)
  }
}

object FacebookTestHelper {
  val wishkeeperFacebookTestAppId = "1376924702342472"
  val wishkeeperFacebookTestAppSecret = "3f5ee9ef27bd152217246ab02bed5725"
  val access_token = s"$wishkeeperFacebookTestAppId|$wishkeeperFacebookTestAppSecret"
}