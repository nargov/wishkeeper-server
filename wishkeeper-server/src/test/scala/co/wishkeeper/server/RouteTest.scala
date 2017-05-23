package co.wishkeeper.server

import java.util.UUID
import java.util.UUID.randomUUID

import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.Specs2RouteTest
import co.wishkeeper.server.Commands.{ConnectFacebookUser, SendFriendRequest}
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

      val webApi = new WebApi(null, null, userProfileProjection, null, null, null, null)
      val name = "Joe"

      checking {
        allowing(userProfileProjection).get(having(any[UUID])).willReturn(UserProfile(name = Option(name)))
      }

      Get(s"/users/${randomUUID()}/profile") ~> webApi.managementRoute ~> check {
        handled should beTrue
        responseAs[UserProfile].name should beSome(name)
      }
    }
  }

  trait LoggedInUserContext {
    val sessionId = randomUUID()
    val userId = randomUUID()
    val sessionIdHeader = RawHeader(WebApi.sessionIdHeader, sessionId.toString)
    val dataStore = mock[DataStore]
  }

  trait NotLoggedInContext {
    val commandProcessor = mock[CommandProcessor]
    val dataStore = mock[DataStore]
    val facebookConnector = mock[FacebookConnector]
    val token = "auth-token"
    val connectFacebookUser = ConnectFacebookUser("facebook-id", token, randomUUID()).asJson.noSpaces

    val webApi = new WebApi(commandProcessor, null, null, dataStore, null, facebookConnector, null)
  }

  "Route" should {
    "Return a list of facebook friends that are wishkeeper users" in new LoggedInUserContext {
      val accessTokenHeader = RawHeader(WebApi.facebookAccessTokenHeader, "access-token")
      val facebookId = "facebookId"
      val potentialFriends = List(PotentialFriend(randomUUID(), "Alice", "link"), PotentialFriend(randomUUID(), "Bob", "link"))

      val userProfileProjection = mock[UserProfileProjection]
      val userFriendsProjection = mock[UserFriendsProjection]
      val webApi = new WebApi(null, null, userProfileProjection, dataStore, userFriendsProjection, null, null)

      checking {
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
      val potentialFriendId = randomUUID()
      val friendRequest = SendFriendRequest(potentialFriendId)
      val commandProcessor = mock[CommandProcessor]

      val webApi = new WebApi(commandProcessor, null, null, dataStore, null, null, null)

      checking {
        ignoring(dataStore)
        oneOf(commandProcessor).process(friendRequest, Some(sessionId))
      }

      Post(s"/users/friends/request").
        withHeaders(sessionIdHeader).
        withEntity(`application/json`, friendRequest.asJson.noSpaces) ~> webApi.userRoute ~> check {

        handled must beTrue
      }
    }

    "Validate facebook token" in new NotLoggedInContext {

      checking {
        ignoring(commandProcessor)
        ignoring(dataStore)
        oneOf(facebookConnector).isValid(token).willReturn(Future.successful(true))
      }

      Post("/users/connect/facebook").withEntity(`application/json`, connectFacebookUser) ~> webApi.userRoute ~> check {
        ok
      }
    }

    "Reject on invalid facebook token" in new NotLoggedInContext {

      checking {
        ignoring(commandProcessor)
        ignoring(dataStore)
        oneOf(facebookConnector).isValid(token).willReturn(Future.successful(false))
      }

      Post("/users/connect/facebook").withEntity(`application/json`, connectFacebookUser) ~> Route.seal(webApi.userRoute) ~> check {
        status must beEqualTo(StatusCodes.Forbidden)
      }
    }

    "return existing incoming friend requests" in new LoggedInUserContext {
      val incomingFriendRequestsProjection = mock[IncomingFriendRequestsProjection]

      val webApi = new WebApi(null, null, null, dataStore, null, null, incomingFriendRequestsProjection)
      val friendRequestSender = randomUUID()

      checking {
        allowing(dataStore).userBySession(sessionId).willReturn(Option(userId))
        oneOf(incomingFriendRequestsProjection).awaitingApproval(userId).willReturn(List(friendRequestSender))
      }

      Get("/users/friends/requests/incoming").withHeaders(sessionIdHeader) ~> webApi.userRoute ~> check {
        responseAs[List[UUID]] must beEqualTo(List(friendRequestSender))
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