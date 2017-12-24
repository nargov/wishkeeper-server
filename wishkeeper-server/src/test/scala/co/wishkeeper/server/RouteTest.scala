package co.wishkeeper.server

import java.util.UUID
import java.util.UUID.randomUUID

import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.testkit.Specs2RouteTest
import co.wishkeeper.json._
import co.wishkeeper.server.Commands.{ConnectFacebookUser, SendFriendRequest, SetFlagFacebookFriendsListSeen}
import co.wishkeeper.server.NotificationsData.FriendRequestNotification
import co.wishkeeper.server.api.{ManagementApi, NotFriends, PublicApi, ValidationError}
import co.wishkeeper.server.image.ImageMetadata
import co.wishkeeper.server.projections.{Friend, PotentialFriend, UserFriends}
import co.wishkeeper.server.web.{ManagementRoute, WebApi}
import com.wixpress.common.specs2.JMock
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import io.circe.syntax._
import org.specs2.matcher.Matcher
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import scala.concurrent.Future
import scala.util.{Failure, Success}


class RouteTest extends Specification with Specs2RouteTest with JMock {

  implicit val circeConfig = Configuration.default.withDefaults

  trait ManagementContext extends Scope {
    val managementApi: ManagementApi = mock[ManagementApi]
    val managementRoute = ManagementRoute(managementApi)
    val userId: UUID = randomUUID()
  }

  "Management Route" should {
    "Return a user profile" in new ManagementContext {
      val name = "Joe"

      checking {
        allowing(managementApi).profileFor(having(any[UUID])).willReturn(UserProfile(name = Option(name)))
      }

      Get(s"/users/${randomUUID()}/profile") ~> managementRoute ~> check {
        handled should beTrue
        responseAs[UserProfile].name should beSome(name)
      }
    }

    "Reset flag facebook friends seen" in new ManagementContext {

      checking {
        oneOf(managementApi).resetFacebookFriendsSeenFlag(userId)
      }

      Delete(s"/users/$userId/flags/facebook-friends") ~> managementRoute ~> check {
        handled should beTrue
        status should beEqualTo(StatusCodes.OK)
      }
    }

    "Return a user id by email" in new ManagementContext {
      val email = "blip@blup.com"

      checking {
        oneOf(managementApi).userByEmail(email).willReturn(Option(userId))
      }

      Get(s"/users/email/$email/id") ~> managementRoute ~> check {
        handled should beTrue
        responseAs[UUID] must beEqualTo(userId)
      }
    }
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
      val friendRequest = SendFriendRequest(randomUUID())

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

    "Set facebook friends list flag" in new LoggedInUserContext {
      checking {
        oneOf(publicApi).processCommand(SetFlagFacebookFriendsListSeen(), Option(sessionId))
      }

      Post(s"/users/flags/facebook-friends").withHeaders(sessionIdHeader) ~> webApi.userRoute ~> check {
        status must beEqualTo(StatusCodes.OK)
      }
    }

    "get flags" in new LoggedInUserContext {
      checking {
        allowing(publicApi).userFlagsFor(sessionId).willReturn(Flags(seenFacebookFriendsList = true))
      }

      Get(s"/users/flags").withHeaders(sessionIdHeader) ~> webApi.userRoute ~> check {
        responseAs[Flags].seenFacebookFriendsList must beTrue
      }
    }

    "get notifications" in new LoggedInUserContext {
      val notificationData = FriendRequestNotification(friendId, requestId)
      checking {
        allowing(publicApi).notificationsFor(sessionId).willReturn(UserNotifications(List(Notification(randomUUID(), notificationData)), 1))
      }

      Get(s"/users/notifications").withHeaders(sessionIdHeader) ~> webApi.userRoute ~> check {
        responseAs[UserNotifications].list match {
          case x :: xs => x.data must beEqualTo(notificationData)
        }
      }
    }

    "approve friend request" in new LoggedInUserContext {
      val reqId = randomUUID()

      checking {
        allowing(publicApi).approveFriendRequest(sessionId, reqId)
      }

      Post(s"/users/notifications/friendreq/$reqId/approve").withHeaders(sessionIdHeader) ~> webApi.userRoute ~> check {
        handled must beTrue
        status must beEqualTo(StatusCodes.OK)
      }
    }

    "ignore friend request" in new LoggedInUserContext {
      val reqId = randomUUID()

      checking {
        allowing(publicApi).ignoreFriendRequest(sessionId, reqId)
      }

      Post(s"/users/notifications/friendreq/$reqId/ignore").withHeaders(sessionIdHeader) ~> webApi.userRoute ~> check {
        handled must beTrue
        status must beEqualTo(StatusCodes.OK)
      }
    }

    "return friends list" in new LoggedInUserContext {
      val userFriends = UserFriends(List(Friend(friendId, Some("friend name"), Some("http://friend.image.com"))))
      checking {
        allowing(publicApi).friendsListFor(sessionId).willReturn(userFriends)
      }

      Get(s"/users/friends").withHeaders(sessionIdHeader) ~> webApi.userRoute ~> check {
        responseAs[UserFriends] must beEqualTo(userFriends)
      }
    }

    "return friend profile" in new LoggedInUserContext {
      val userProfile = UserProfile(email = Some("expected@email.com"))
      checking {
        allowing(publicApi).userProfileFor(sessionId, friendId).willReturn(Right(userProfile))
      }

      Get(s"/users/profile/${friendId.toString}/").withHeaders(sessionIdHeader) ~> webApi.userRoute ~> check {
        responseAs[UserProfile] must beEqualTo(userProfile)
      }
    }

    "return not friends" in new LoggedInUserContext {
      checking {
        allowing(publicApi).userProfileFor(sessionId, friendId).willReturn(Left(NotFriends))
      }

      Get(s"/users/profile/${friendId.toString}/").withHeaders(sessionIdHeader) ~> webApi.userRoute ~> check {
        status must beEqualTo(StatusCodes.Forbidden)
      }
    }

    "return friend wishes" in new LoggedInUserContext {
      val friendWishes = UserWishes(List(Wish(randomUUID(), name = Some("friend wish"))))

      checking {
        allowing(publicApi).wishListFor(sessionId, friendId).willReturn(Right(friendWishes))
      }

      Get(s"/users/wishes/${friendId.toString}").withHeaders(sessionIdHeader) ~> webApi.userRoute ~> check {
        responseAs[UserWishes] must beEqualTo(friendWishes)
      }
    }

    "return not friends" in new LoggedInUserContext {
      checking {
        allowing(publicApi).wishListFor(sessionId, friendId).willReturn(Left(NotFriends))
      }

      Get(s"/users/wishes/${friendId.toString}").withHeaders(sessionIdHeader) ~> webApi.userRoute ~> check {
        status must beEqualTo(StatusCodes.Forbidden)
        responseAs[ValidationError] must beEqualTo(NotFriends)
      }
    }

    "return friend friends" in new LoggedInUserContext {
      val friends = UserFriends(List(Friend(friendId, None, None)))
      checking {
        oneOf(publicApi).friendsListFor(sessionId, friendId).willReturn(Right(friends))
      }

      Get(s"/users/${friendId.toString}/friends").withHeaders(sessionIdHeader) ~> webApi.userRoute ~> check {
        responseAs[UserFriends] must beEqualTo(friends)
      }
    }

    "mark notifications as viewed" in new LoggedInUserContext {
      checking {
        oneOf(publicApi).markAllNotificationsViewed(sessionId)
      }

      Post(s"/users/notifications/all/viewed").withHeaders(sessionIdHeader) ~> webApi.userRoute ~> check {
        handled must beTrue
      }
    }

    "unfriend" in new LoggedInUserContext {
      checking{
        oneOf(publicApi).unfriend(sessionId, friendId).willReturn(Right(()))
      }

      Delete(s"/users/${friendId.toString}").withHeaders(sessionIdHeader) ~> webApi.userRoute ~> check {
        handled must beTrue
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
    val friendId = randomUUID()
    val requestId = randomUUID()
    val sessionIdHeader = RawHeader(WebApi.sessionIdHeader, sessionId.toString)
  }

  trait NotLoggedInContext extends BaseContext {
    val token = "auth-token"
    val connectFacebookUser = ConnectFacebookUser("facebook-id", token, randomUUID())
  }

  def aPotentialFriendWith(id: UUID, name: String): Matcher[PotentialFriend] =
    (===(name) ^^ {
      (_: PotentialFriend).name
    }) and (===(id) ^^ {
      (_: PotentialFriend).userId
    })

}