package co.wishkeeper.server

import java.util.UUID.randomUUID

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import co.wishkeeper.json._
import co.wishkeeper.server.Commands.{ConnectFacebookUser, SendFriendRequest}
import co.wishkeeper.server.HttpTestKit.{Get, Post}
import co.wishkeeper.server.NotificationsData.FriendRequestNotification
import co.wishkeeper.server.projections.PotentialFriend
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Try

class TestUsersHelper(server: String = s"http://localhost:12300/users",
                      appId: String,
                      appSecret: String) {

  println(s"Test Users Helper working on $server")

  private val usersEndpoint = server + "/users"

  private implicit val circeConfig = Configuration.default.withDefaults
  private implicit val system = ActorSystem()
  private implicit val ec = system.dispatcher
  private implicit val am = ActorMaterializer()
  val facebookTestHelper = new FacebookTestHelper

  @tailrec
  private def makeFriends(testUsers: Seq[TestFacebookUser]): Unit = testUsers match {
    case x :: xs if xs.nonEmpty =>
      println(s"making ${x.email} friend with ${xs.map(_.email)}")
      facebookTestHelper.makeFriendsAsync(x, xs)
      makeFriends(xs)
    case _ =>
  }

  def createTestUsers(num: Int = 5): Unit = {
    val accessToken = appId + "|" + appSecret
    val sessionIdHeader = "wsid"
    val facebookAccessTokenHeader = "fbat"
    val users: List[TestFacebookUser] = facebookTestHelper.createTestUsers(num, installApp = true, accessToken, appId)

    val usersWithTokens: Future[List[TestFacebookUser]] = facebookTestHelper.addAccessTokens(users, appId, accessToken)
    val usersWithSessionIds = usersWithTokens.map(_.map(_.copy(sessionId = Option(randomUUID()))))
    val futureFriends = usersWithSessionIds.map(makeFriends)
    val futureLogins = sendConnectRequests(usersWithSessionIds)

    Await.result(Future.sequence(Seq(usersWithSessionIds, futureFriends, futureLogins)), 2.minutes) match {
      case testUsers :: _ :: _ =>
        val friends = testUsers.asInstanceOf[List[TestFacebookUser]]
        sendFriendRequests(sessionIdHeader, facebookAccessTokenHeader, friends)
        approveFriendRequests(sessionIdHeader, friends)
    }

    println("######################################################")
    println("####################    Test Users    ################\n")
    users.
      map(u => s"${u.email} ${u.password} ${u.userProfile.flatMap(_.name).getOrElse("")}").foreach(println)
    System.exit(0)
  }

  private def withRetries[T](f: => T, retries: Int = 10, delay: Duration = 1.second): T = {
    try {
      f
    }
    catch {
      case e: Exception =>
        Thread.sleep(delay.toMillis)
        if (retries > 0)
          withRetries(f, retries - 1, delay)
        else throw e
    }
  }

  private def sendConnectRequests(usersWithSessionIds: Future[List[TestFacebookUser]]) = {
    usersWithSessionIds.flatMap { userList =>
      val connectRequests: Seq[ConnectFacebookUser] = userList.map(user =>
        ConnectFacebookUser(user.id, user.access_token, user.sessionId.getOrElse(randomUUID())))
      Future.sequence(connectRequests.map(Post.async(s"$usersEndpoint/connect/facebook", _)))
    }
  }

  private def sendFriendRequests(sessionIdHeader: String, facebookAccessTokenHeader: String, friends: List[TestFacebookUser]) = {
    val headers = Map(facebookAccessTokenHeader -> friends.head.access_token, sessionIdHeader -> friends.head.sessionId.get.toString)
    val potentialFriends = withRetries(getPotentialFriends(headers, friends.size - 1))
    val futureFriendRequests = potentialFriends.map { potentialFriend =>
      Post.async(s"$usersEndpoint/friends/request", SendFriendRequest(potentialFriend.userId), headers)
    }
    Await.ready(Future.sequence(futureFriendRequests), 10.seconds)
  }

  private def getPotentialFriends(headers: Map[String, String], expectedSize: Int): List[PotentialFriend] = {
    val potentialFriends = Get(s"$usersEndpoint/friends/facebook", headers).to[List[PotentialFriend]]
    if (potentialFriends.size != expectedSize)
      throw new IllegalStateException("Wrong number of potential friends. Facebook friends api probably not in sync.")
    potentialFriends
  }

  private def approveFriendRequests(sessionIdHeader: String, friends: List[TestFacebookUser]) = {
    def doStuff(friend: TestFacebookUser): Future[HttpTestKit.Response] = {
      val futureNotifications: Future[UserNotifications] =
        Get.async(s"$usersEndpoint/notifications", Map(sessionIdHeader -> friend.sessionId.get.toString)).
          map(_.to[UserNotifications])
      futureNotifications.flatMap { notifications =>
        val friendRequestNotification = notifications.list.head.data.asInstanceOf[FriendRequestNotification]
        Post.async(s"$usersEndpoint/notifications/friendreq/${friendRequestNotification.requestId}/approve", (),
          Map(sessionIdHeader -> friend.sessionId.get.toString))
      }
    }

    def retryFuture[T](f: => Future[T], retries: Int = 10, delay: Duration = 100.millis): Future[T] = {
      val result = f
      result.onFailure {
        case e: Exception =>
          println(e.getMessage)
          println(s"retrying in $delay")
          Thread.sleep(delay.toMillis)
          retryFuture(f, retries - 1, delay)
      }
      result
    }

    val approveFriendRequestsResults = friends.tail.map { friend =>
      retryFuture(doStuff(friend))
    }
    Await.ready(Future.sequence(approveFriendRequestsResults), 10.seconds)
  }
}

object TestUsersHelper {
  def main(args: Array[String]): Unit = {
    val params = args.toList.grouped(2).foldLeft(Map[String, String]())((m, l) => m + (l.head -> l(1)))
    val helper = new TestUsersHelper(server = params("-server"), appId = params("-app-id"), appSecret = params("-app-secret"))

    Try {
      helper.createTestUsers()
    }.recover { case e: Exception => e.printStackTrace() }
  }
}