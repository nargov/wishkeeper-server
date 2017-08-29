package co.wishkeeper.server

import java.util.UUID
import java.util.UUID.randomUUID

import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.testkit.Specs2RouteTest
import co.wishkeeper.json._
import co.wishkeeper.server.Commands.{ConnectFacebookUser, SendFriendRequest}
import co.wishkeeper.server.api.{ManagementApi, PublicApi}
import co.wishkeeper.server.image.ImageMetadata
import co.wishkeeper.server.projections.PotentialFriend
import com.wixpress.common.specs2.JMock
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.circe.generic.auto._
import io.circe.syntax._
import org.specs2.matcher.Matcher
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import scala.concurrent.Future
import scala.util.{Failure, Success}


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
        oneOf(publicApi).connectFacebookUser(connectFacebookUser).willReturn(Future.successful(true))
      }

      Post("/users/connect/facebook").withEntity(`application/json`, connectFacebookUser.asJson.noSpaces) ~> webApi.userRoute ~> check {
        status must beEqualTo(StatusCodes.OK)
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

    "Return wish list" in new LoggedInUserContext {
      val wishes = UserWishes(List(Wish(randomUUID())))

      checking {
        allowing(publicApi).wishListFor(sessionId).willReturn(Some(wishes))
      }

      Get("/users/wishes").withHeaders(sessionIdHeader) ~> webApi.userRoute ~> check {
        responseAs[UserWishes] must beEqualTo(wishes)
      }
    }

    "Delete Wish" in new LoggedInUserContext {
      val wishId = randomUUID()

      checking {
        oneOf(publicApi).deleteWish(sessionId, wishId)
      }

      Delete(s"/users/wishes/$wishId").withHeaders(sessionIdHeader) ~> webApi.userRoute ~> check {
        status must beEqualTo(StatusCodes.OK)
      }
    }

    "upload image through url" in new LoggedInUserContext {
      val wishId = randomUUID()
      val imageMetadata = ImageMetadata("content-type", "filename", width = 1, height = 1)
      val url = "http://my.image.url"

      checking {
        oneOf(publicApi).uploadImage(url, imageMetadata, wishId, sessionId).willReturn(Success(()))
      }

      val imageDimensionsHeader = RawHeader(WebApi.imageDimensionsHeader, s"${imageMetadata.width},${imageMetadata.height}")
      val params = s"filename=${imageMetadata.fileName}&contentType=${imageMetadata.contentType}&url=$url"
      Post(s"/users/wishes/$wishId/image/url?$params").withHeaders(sessionIdHeader, imageDimensionsHeader) ~> webApi.userRoute ~> check {
        status must beEqualTo(StatusCodes.Created)
      }
    }

    "print error on upload failure" in new LoggedInUserContext {
      val wishId = randomUUID()
      val imageMetadata = ImageMetadata("content-type", "filename", width = 1, height = 1)
      val url = "http://my.image.url"

      checking {
        oneOf(publicApi).uploadImage(url, imageMetadata, wishId, sessionId).willReturn(Failure(new RuntimeException("Shit happened")))
      }

      val imageDimensionsHeader = RawHeader(WebApi.imageDimensionsHeader, s"${imageMetadata.width},${imageMetadata.height}")
      val params = s"filename=${imageMetadata.fileName}&contentType=${imageMetadata.contentType}&url=$url"
      Post(s"/users/wishes/$wishId/image/url?$params").withHeaders(sessionIdHeader, imageDimensionsHeader) ~> webApi.userRoute ~> check {
        status must beEqualTo(StatusCodes.InternalServerError)
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