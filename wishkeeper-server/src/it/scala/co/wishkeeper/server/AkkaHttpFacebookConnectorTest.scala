package co.wishkeeper.server

import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import co.wishkeeper.server.FacebookTestHelper.{testAppId, testAppSecret}
import org.joda.time.DateTime
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.Matcher
import org.specs2.mutable.Specification
import org.specs2.specification.{AfterAll, Scope}

import scala.concurrent.duration._

class AkkaHttpFacebookConnectorTest extends Specification with AfterAll {

  implicit val executionEnv: ExecutionEnv = ExecutionEnv.fromExecutionContext(ExecutionEnv.createExecutionContext(
    Executors.newCachedThreadPool(), verbose = false, println))

  implicit val system = ActorSystem("test-system")
  implicit val materializer = ActorMaterializer()

  val facebookTestHelper = new FacebookTestHelper

  trait Context extends Scope {
    val facebookAdapter: FacebookConnector = new AkkaHttpFacebookConnector(testAppId, testAppSecret)
  }

  "return the list of friends" in new Context {
    val testUsers: List[TestFacebookUser] = facebookTestHelper.createTestUsers(2, installApp = true)
    facebookTestHelper.makeFriends(testUsers.head, testUsers.tail)

    eventually(10, 0.millis) {
      val friends = facebookAdapter.friendsFor(testUsers.head.id, testUsers.head.access_token)
      friends must beSameFriendsAs(testUsers.tail).await(80, 250.millis)
    }
  }

  "return the list of many friends" in new Context {
    val testUsers = facebookTestHelper.createTestUsers(30, installApp = true)
    testUsers.size must beEqualTo(30)
    println("make friends begin: " + DateTime.now().toString)
    facebookTestHelper.makeFriends(testUsers.head, testUsers.tail)
    println("make friends end: " + DateTime.now().toString)

    eventually(5, 0.millis) {
      val friends = facebookAdapter.friendsFor(testUsers.head.id, testUsers.head.access_token)
      friends must beSameFriendsAs(testUsers.tail).await(80, 250.millis)
    }
  }

  "return true if user access token is valid" in new Context {
    val testUser = facebookTestHelper.createPreInstalledTestUser()

    val eventualResult = facebookAdapter.isValid(testUser.access_token)
    eventualResult must beTrue.await(80, 250.millis)
  }

  def beSameFriendsAs(users: List[TestFacebookUser]): Matcher[List[FacebookFriend]] =
    containTheSameElementsAs(users.map(user => FacebookFriend(user.userProfile.get.name.get, user.id)))

  override def afterAll(): Unit = {
    facebookTestHelper.deleteTestUsers()
    system.terminate()
    materializer.shutdown()
  }
}