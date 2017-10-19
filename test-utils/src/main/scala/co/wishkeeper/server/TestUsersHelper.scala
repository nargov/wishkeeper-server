package co.wishkeeper.server

import java.util.UUID.randomUUID

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import co.wishkeeper.server.Commands.{ConnectFacebookUser, SendFriendRequest}
import co.wishkeeper.server.HttpTestKit.{Get, Post}
import co.wishkeeper.server.projections.PotentialFriend
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Try

class TestUsersHelper {
  val usersEndpoint = s"http://localhost:12300/users"

  private implicit val circeConfig = Configuration.default.withDefaults
  private implicit val system = ActorSystem()
  private implicit val ec = system.dispatcher
  private implicit val am = ActorMaterializer()

  def createTestUsers(num: Int = 5): Unit = {
    val facebookTestHelper = new FacebookTestHelper
    val testAppId = "1415281005229066"
    val accessToken = testAppId + "|22ae61c929a8276caf39357bd787b90d"
    val sessionIdHeader = "wsid"
    val facebookAccessTokenHeader = "fbat"
    val users: List[TestFacebookUser] = facebookTestHelper.createTestUsers(num, installApp = true, accessToken, testAppId)

    @tailrec
    def makeFriends(testUsers: Seq[TestFacebookUser]): Unit = testUsers match {
      case x :: xs if xs.nonEmpty =>
        println(s"making ${x.email} friend with ${xs.map(_.email)}")
        facebookTestHelper.makeFriendsAsync(x, xs)
        makeFriends(xs)
      case _ =>
    }

    val usersWithTokens: Future[List[TestFacebookUser]] = facebookTestHelper.addAccessTokens(users, testAppId, accessToken)
    val usersWithSessionIds = usersWithTokens.map(_.map(_.copy(sessionId = Option(randomUUID()))))
    val futureFriends = usersWithSessionIds.map(makeFriends)
    val futurePasswordSet = usersWithSessionIds.flatMap(facebookTestHelper.setPasswordTo(_, "zxcasdqwe", accessToken))
    val futureLogins = usersWithSessionIds.flatMap { userList =>
      val connectRequests: Seq[ConnectFacebookUser] = userList.map(user =>
        ConnectFacebookUser(user.id, user.access_token, user.sessionId.getOrElse(randomUUID())))
      Future.sequence(connectRequests.map(Post.async(s"$usersEndpoint/connect/facebook", _)))
    }

    Await.result(Future.sequence(Seq(futureFriends, futurePasswordSet, futureLogins)), 2.minutes) match {
      case _ :: testUsers :: _ =>
        val friends = testUsers.asInstanceOf[List[TestFacebookUser]]
        val potentialFriends = Get(s"$usersEndpoint/friends/facebook",
          Map(facebookAccessTokenHeader -> friends.head.access_token, sessionIdHeader -> friends.head.sessionId.get.toString)).
          to[List[PotentialFriend]]
        val futureFriendRequests = potentialFriends.map { potentialFriend =>
          Post.async(s"$usersEndpoint/friends/request", SendFriendRequest(potentialFriend.userId))
        }
        Await.ready(Future.sequence(futureFriendRequests), 10.seconds)
//        val approveFriendRequestsResults = friends.tail.map { friend =>
//          val futureNotifications: Future[UserNotifications] =
//            Get.async(s"$usersEndpoint/notifications", Map(sessionIdHeader -> friend.sessionId.get.toString)).
//              map(_.to[UserNotifications])
//          futureNotifications.flatMap { notifications =>
//            val friendRequestNotification = notifications.list.head.data.asInstanceOf[FriendRequestNotification]
//            Post.async(s"$usersEndpoint/notifications/friendreq/${friendRequestNotification.requestId}/approve", (),
//              Map(sessionIdHeader -> friend.sessionId.get.toString))
//          }
//        }
//        Await.ready(Future.sequence(approveFriendRequestsResults), 10.seconds)
    }

    println("Done")
    System.exit(0)
  }
}

object TestUsersHelper {
  def main(args: Array[String]): Unit = {
    val helper = new TestUsersHelper()
    Try {
      helper.createTestUsers()
    }.recover { case e: Exception => e.printStackTrace() }
  }
}