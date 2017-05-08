package co.wishkeeper.server

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.Matcher
import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll

import scala.concurrent.duration._

class FacebookRESTAdapterTest(implicit ee: ExecutionEnv) extends Specification with AfterAll {
  implicit val system = ActorSystem("test-system")
  implicit val materializer = ActorMaterializer()

  val facebookTestHelper = new FacebookTestHelper

  "return the list of friends" in {
    val testUsers: List[TestFacebookUser] = facebookTestHelper.createTestUsers(2, installApp = true)
    facebookTestHelper.makeFriends(testUsers.head, testUsers.tail.head)

    val facebookAdapter: FacebookConnector = new AkkaHttpFacebookConnector()

    eventually(10, 0.millis) {
      val friends = facebookAdapter.friendsFor(testUsers.head.id, testUsers.head.accessToken)
      friends must beSameFriendsAs(testUsers.tail).await(80, 250.millis)
    }
  }

  def beSameFriendsAs(users: List[TestFacebookUser]): Matcher[List[FacebookFriend]] =
    be_==(users.map(user => FacebookFriend(user.userProfile.name.get, user.id)))

  override def afterAll(): Unit = {
    facebookTestHelper.deleteTestUsers()
    system.terminate()
  }
}