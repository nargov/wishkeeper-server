package co.wishkeeper.server

import java.util.UUID
import java.util.UUID.randomUUID

import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.Specs2RouteTest
import co.wishkeeper.server.Commands.{ConnectFacebookUser, CreateNewWish, SendFriendRequest}
import co.wishkeeper.server.api.{ManagementApi, PublicApi}
import co.wishkeeper.server.projections.{UserFriendsProjection, UserProfileProjection}
import com.wixpress.common.specs2.JMock
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.circe.generic.auto._
import io.circe.syntax._
import org.specs2.matcher.Matcher
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import scala.concurrent.Future


class RouteTest extends Specification with Specs2RouteTest with JMock {
  "Management Route" should {
    "Return a user profile" in {
      val managementApi = mock[ManagementApi]

      val webApi = new WebApi(null, managementApi)
      val name = "Joe"

      checking {
        allowing(managementApi).profileFor(having(any[UUID])).willReturn(UserProfile(name = Option(name)))
      }

      Get(s"/users/${randomUUID()}/profile") ~> webApi.managementRoute ~> check {
        handled should beTrue
        responseAs[UserProfile].name should beSome(name)
      }
    }
  }

  trait BaseContext extends Scope {
    val publicApi = mock[PublicApi]
    val managementApi = mock[ManagementApi]

    val webApi = new WebApi(publicApi, managementApi)
  }

  trait LoggedInUserContext extends BaseContext {
    val sessionId = randomUUID()
    val userId = randomUUID()
    val sessionIdHeader = RawHeader(WebApi.sessionIdHeader, sessionId.toString)
  }

  trait NotLoggedInContext extends BaseContext {
    val token = "auth-token"
    val connectFacebookUser = ConnectFacebookUser("facebook-id", token, randomUUID())
  }

  "Route" should {
    "Return a list of facebook friends that are wishkeeper users" in new LoggedInUserContext {
      val accessToken = "access-token"
      val accessTokenHeader = RawHeader(WebApi.facebookAccessTokenHeader, accessToken)
      val potentialFriends = List(PotentialFriend(randomUUID(), "Alice", "link"), PotentialFriend(randomUUID(), "Bob", "link"))

      checking {
        allowing(publicApi).potentialFriendsFor(having(===(accessToken)), having(any)).willReturn(Some(Future.successful(potentialFriends)))
      }

      Get(s"/users/friends/facebook").withHeaders(sessionIdHeader, accessTokenHeader) ~> webApi.userRoute ~> check {
        handled must beTrue
        responseAs[List[PotentialFriend]] should beEqualTo(potentialFriends)
      }
    }

    "Allow a user to send a friend request" in new LoggedInUserContext {
      val potentialFriendId = randomUUID()
      val friendRequest = SendFriendRequest(potentialFriendId)

      checking {
        oneOf(publicApi).processCommand(friendRequest, Some(sessionId))
      }

      Post(s"/users/friends/request").
        withHeaders(sessionIdHeader).
        withEntity(`application/json`, friendRequest.asJson.noSpaces) ~> webApi.userRoute ~> check {

        handled must beTrue
      }
    }

    "Validate facebook token" in new NotLoggedInContext {

      checking {
        oneOf(publicApi).connectFacebookUser(connectFacebookUser)
      }

      Post("/users/connect/facebook").withEntity(`application/json`, connectFacebookUser.asJson.noSpaces) ~> webApi.userRoute ~> check {
        ok
      }
    }

    "return existing incoming friend requests" in new LoggedInUserContext {
      val friendRequestSender = randomUUID()

      checking {
        oneOf(publicApi).incomingFriendRequestSenders(sessionId).willReturn(Some(List(friendRequestSender)))
      }

      Get("/users/friends/requests/incoming").withHeaders(sessionIdHeader) ~> webApi.userRoute ~> check {
        responseAs[List[UUID]] must beEqualTo(List(friendRequestSender))
      }
    }

    "return user profile" in new LoggedInUserContext {
      val expectedProfile = UserProfile(
        socialData = Some(SocialData(Some("facebook-id"))),
        ageRange = Some(AgeRange(Some(15), Some(20))),
        email = Some("me@something.com"),
        name = Some("My Name"),
        gender = Some("female"))

      checking {
        allowing(publicApi).userProfileFor(sessionId).willReturn(Option(expectedProfile))
      }

      Get("/users/profile").withHeaders(sessionIdHeader) ~> webApi.userRoute ~> check {
        responseAs[UserProfile] must beEqualTo(expectedProfile)
      }
    }

    "Create new wish" in new LoggedInUserContext {
      val createNewWish = CreateNewWish(randomUUID())

      checking {
        oneOf(publicApi).processCommand(createNewWish, Option(sessionId))
      }

      Post(s"/users/wishes").
        withHeaders(sessionIdHeader).
        withEntity(`application/json`, createNewWish.asJson.noSpaces) ~> webApi.userRoute ~> check {
        handled must beTrue
      }
    }
  }

  def aPotentialFriendWith(id: UUID, name: String): Matcher[PotentialFriend] =
    (===(name) ^^ {
      (_: PotentialFriend).name
    }) and (===(id) ^^ {
      (_: PotentialFriend).userId
    })

}