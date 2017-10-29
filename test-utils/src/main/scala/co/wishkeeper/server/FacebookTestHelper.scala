package co.wishkeeper.server

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import java.util.function.UnaryOperator

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods.POST
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, StatusCodes}
import akka.stream.ActorMaterializer
import co.wishkeeper.server.FacebookTestHelper.{access_token, testAppId}
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import io.circe.parser.decode

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Success


class FacebookTestHelper(implicit ec: ExecutionContext, actorSystem: ActorSystem, materializer: ActorMaterializer) {

  implicit val circeConfig = Configuration.default.withDefaults

  private val testFbUsers = new AtomicReference[Map[String, TestFacebookUser]](Map.empty)

  def makeFriendsAsync(testUser: TestFacebookUser, friends: List[TestFacebookUser]): Seq[Future[HttpResponse]] = {
    friends.map { friend =>
      friendRequestFor(testUser, friend).flatMap {
        case res if res.status == StatusCodes.OK => friendRequestFor(friend, testUser)
        case res =>
          println("Error making friends " + res.entity.dataBytes.fold("")(_ + _.mkString))
          Future.successful(res)
      }
    }
  }

  def makeFriends(testUser: TestFacebookUser, friends: List[TestFacebookUser]): Unit = {
    val eventualFriends = makeFriendsAsync(testUser, friends)

    val results = Await.result(Future.sequence(eventualFriends), 20.seconds * friends.size)
    if (results.exists(_.status != StatusCodes.OK)) throw new IllegalStateException("Failed to create relationships")
  }

  private def friendRequestFor(testUser: TestFacebookUser, friend: TestFacebookUser): Future[HttpResponse] = {
    Http().singleRequest(HttpRequest().
      withUri(s"https://graph.facebook.com/v2.9/${testUser.id}/friends/${friend.id}?access_token=${testUser.access_token}").
      withMethod(POST))
  }

  def addAccessToken(user: TestFacebookUser): Future[TestFacebookUser] = addAccessTokens(user :: Nil).map(_.head)

  def addAccessTokens(users: List[TestFacebookUser], testAppId: String = testAppId,
                      accessToken: String = access_token): Future[List[TestFacebookUser]] = {
    val eventualResponse = Http().singleRequest(HttpRequest().
      withUri(s"https://graph.facebook.com/v2.9/$testAppId/accounts/test-users?access_token=$accessToken"))
    val eventualJson: Future[String] = eventualResponse.flatMap(_.entity.dataBytes.runFold("")(_ + _.utf8String))
    val eventualUsersAccessDataList = eventualJson.map(json => decode[TestUsersAccessDataList](json)).map {
      case Right(list) => list
      case Left(err) => throw err
    }
    val tokensMap = eventualUsersAccessDataList.map(list => list.data.map(_.id).zip(list.data).toMap)
    tokensMap.map(tokens => users.map(user => user.copy(access_token = tokens(user.id).access_token)))
  }

  def setPasswordTo(users: List[TestFacebookUser], password: String, accessToken: String = access_token): Future[List[TestFacebookUser]] = {
    val futurePasswordSetResponses = Future.sequence(users.map(user =>
        Http().singleRequest(HttpRequest().withMethod(POST).
          withUri(s"https://graph.facebook.com/v2.9/${user.id}?access_token=$accessToken&password=$password"))))

    futurePasswordSetResponses.map(responses =>
      if(responses.forall(_.status.isSuccess())) {
        println(s"${responses.size} passwords set to $password")
        users.map(_.copy(password = password))
      }
      else throw new IllegalStateException("Error setting password for test user"))
  }

  def createTestUser(): TestFacebookUser = createTestUsers().head

  def createPreInstalledTestUser(): TestFacebookUser = createTestUsers(installApp = true).head

  def createTestUsers(numUsers: Int = 1,
                      installApp: Boolean = false,
                      accessToken: String = access_token,
                      testAppId: String = testAppId): List[TestFacebookUser] = {
    println(s"Creating $numUsers test user${if (numUsers > 1) "s" else ""}")
    val eventualUsers = Future.sequence((1 to numUsers).toList.map { _ =>
      val eventualTestUserResponse = Http().singleRequest(HttpRequest().
        withMethod(POST).
        withUri(s"https://graph.facebook.com/v2.9/$testAppId/accounts/test-users").
        withEntity(s"access_token=$accessToken&installed=$installApp"))
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
  val testAppId = "1376924702342472"
  val testAppSecret = "3f5ee9ef27bd152217246ab02bed5725"
  val access_token = s"$testAppId|$testAppSecret"
}

case class TestUsersAccessDataList(data: List[TestUserAccessToken])

case class TestUserAccessToken(id: String, access_token: String)

case class TestFacebookUser(id: String,
                            email: String,
                            password: String,
                            access_token: String = "",
                            login_url: String,
                            userProfile: Option[UserProfile] = None,
                            sessionId: Option[UUID] = None)
