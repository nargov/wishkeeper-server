package co.wishkeeper.server

import java.util.UUID

import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.testkit.Specs2RouteTest
import co.wishkeeper.server.Commands.SendFriendRequest
import co.wishkeeper.server.projections.{UserFriendsProjection, UserProfileProjection}
import com.wixpress.common.specs2.JMock
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.circe.generic.auto._
import io.circe.syntax._
import org.specs2.matcher.Matcher
import org.specs2.mutable.Specification

import scala.concurrent.Future


class RouteTest extends Specification with Specs2RouteTest with JMock {
  "Management Route" should {
    "Return a user profile" in {
      val userProfileProjection = mock[UserProfileProjection]

      val webApi = new WebApi(null, null, userProfileProjection, null, null)
      val name = "Joe"

      checking {
        allowing(userProfileProjection).get(having(any[UUID])).willReturn(UserProfile(name = Option(name)))
      }

      Get(s"/users/${UUID.randomUUID()}/profile") ~> webApi.managementRoute ~> check {
        handled should beTrue
        responseAs[UserProfile].name should beSome(name)
      }
    }
  }

  trait LoggedInUserContext {
      val sessionId = UUID.randomUUID()
      val userId = UUID.randomUUID()
      val sessionIdHeader = RawHeader(WebApi.sessionIdHeader, sessionId.toString)
      val dataStore = mock[DataStore]
  }

  "Route" should {
    "Return a list of facebook friends that are wishkeeper users" in new LoggedInUserContext {
      val accessTokenHeader = RawHeader(WebApi.facebookAccessTokenHeader, "access-token")
      val facebookId = "facebookId"
      val potentialFriends = List(PotentialFriend(UUID.randomUUID(), "Alice", "link"), PotentialFriend(UUID.randomUUID(), "Bob", "link"))

      val userProfileProjection = mock[UserProfileProjection]
      val userFriendsProjection = mock[UserFriendsProjection]
      val webApi = new WebApi(null, null, userProfileProjection, dataStore, userFriendsProjection)

      checking{
        allowing(dataStore).userBySession(sessionId).willReturn(Some(userId))
        allowing(userProfileProjection).get(userId).willReturn(UserProfile(Some(SocialData(Some(facebookId)))))
        allowing(userFriendsProjection).potentialFacebookFriends(having(===(facebookId)), having(any)).willReturn(Future.successful(potentialFriends))
      }

      Get(s"/users/friends/facebook").withHeaders(sessionIdHeader, accessTokenHeader) ~> webApi.userRoute ~> check {
        handled must beTrue
        responseAs[List[PotentialFriend]] should beEqualTo(potentialFriends)
      }
    }

    "Allow a user to send a friend request" in new LoggedInUserContext {
      val potentialFriendId = UUID.randomUUID()
      val friendRequest = SendFriendRequest(potentialFriendId)
      val commandProcessor = mock[CommandProcessor]

      val webApi = new WebApi(commandProcessor, null, null, dataStore, null)

      checking {
        ignoring(dataStore)
        oneOf(commandProcessor).process(friendRequest, Some(sessionId))
      }

      Post(s"/users/friends/request").
        withHeaders(sessionIdHeader).
        withEntity(ContentTypes.`application/json`, friendRequest.asJson.noSpaces) ~> webApi.userRoute ~> check {

        handled must beTrue
      }
    }
  }

  def aPotentialFriendWith(id: UUID, name: String): Matcher[PotentialFriend] =
    (===(name) ^^ {(_:PotentialFriend).name}) and (===(id) ^^ {(_:PotentialFriend).userId})

}