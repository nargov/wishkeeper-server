package co.wishkeeper.server

import java.util.UUID
import java.util.UUID.randomUUID

import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.testkit.Specs2RouteTest
import co.wishkeeper.json._
import co.wishkeeper.server.NotificationsData.FriendRequestNotification
import co.wishkeeper.server.WishStatus.Deleted
import co.wishkeeper.server.api.{ManagementApi, PublicApi}
import co.wishkeeper.server.image.ImageMetadata
import co.wishkeeper.server.messaging.MemStateClientRegistry
import co.wishkeeper.server.projections.{Friend, FriendBirthdaysResult, PotentialFriend, UserFriends}
import co.wishkeeper.server.search.{SearchQuery, UserSearchResults}
import co.wishkeeper.server.user._
import co.wishkeeper.server.user.commands._
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

  val formContentType = ContentType(MediaTypes.`application/x-www-form-urlencoded`, HttpCharsets.`UTF-8`)

  implicit val circeConfig = Configuration.default.withDefaults

  trait ManagementContext extends Scope {
    val managementApi: ManagementApi = mock[ManagementApi]
    val managementRoute = ManagementRoute(managementApi, new MemStateClientRegistry)
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

    "Delete user picture" in new ManagementContext {

      checking {
        oneOf(managementApi).deleteUserPicture(userId).willReturn(Right(()))
      }

      Delete(s"/users/$userId/profile/picture") ~> managementRoute ~> check {
        handled should beTrue
      }
    }

    "Rebuild user search view" in new ManagementContext {
      checking {
        oneOf(managementApi).rebuildUserSearch()
      }

      Post(s"/views/search/rebuild") ~> managementRoute ~> check {
        handled should beTrue
      }
    }

    "Subscribe all known devices to periodic wakeup topic" in new ManagementContext {
      checking {
        oneOf(managementApi).resubscribePeriodicWakeup()
      }

      Post(s"/subs/periodic/resub") ~> managementRoute ~> check {
        handled should beTrue
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
      checking {
        oneOf(publicApi).deleteWish(userId, wishId).willReturn(Right(()))
      }

      Delete(s"/users/wishes/$wishId").withHeaders(sessionIdHeader) ~> webApi.userRoute ~> check {
        status must beEqualTo(StatusCodes.OK)
      }
    }

    "upload image through url" in new LoggedInUserContext {
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
          case _ =>
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
      checking {
        oneOf(publicApi).unfriend(sessionId, friendId).willReturn(Right(()))
      }

      Delete(s"/users/${friendId.toString}").withHeaders(sessionIdHeader) ~> webApi.userRoute ~> check {
        handled must beTrue
      }
    }

    "grant wish to self" in new LoggedInUserContext {
      checking {
        oneOf(publicApi).grantWish(userId, wishId, None).willReturn(Right(()))
      }

      Post(s"/me/wishes/${wishId.toString}/grant").withHeaders(sessionIdHeader) ~> webApi.newUserRoute ~> check {
        handled must beTrue
        status must beEqualTo(StatusCodes.OK)
      }
    }

    "mark reserved wish granted" in new LoggedInUserContext {
      checking {
        oneOf(publicApi).grantWish(userId, wishId, Option(friendId)).willReturn(Right(()))
      }

      Post(s"/me/wishes/${wishId.toString}/grant?granter=$friendId").withHeaders(sessionIdHeader) ~> webApi.newUserRoute ~> check {
        handled must beTrue
        status must beEqualTo(StatusCodes.OK)
      }
    }

    "reserve wish" in new LoggedInUserContext {
      checking {
        oneOf(publicApi).reserveWish(userId, friendId, wishId).willReturn(Right(()))
      }

      Post(s"/${friendId.toString}/wishes/${wishId.toString}/reserve").withHeaders(sessionIdHeader) ~> webApi.newUserRoute ~> check {
        handled must beTrue
        status must beEqualTo(StatusCodes.OK)
      }
    }

    "return the user's id" in new LoggedInUserContext {
      Get(s"/me/id").withHeaders(sessionIdHeader) ~> webApi.newUserRoute ~> check {
        responseAs[UUID] must beEqualTo(userId)
      }
    }

    "unreserve wish" in new LoggedInUserContext {
      checking {
        oneOf(publicApi).unreserveWish(userId, friendId, wishId).willReturn(Right(()))
      }

      Delete(s"/${friendId.toString}/wishes/${wishId.toString}/reserve").withHeaders(sessionIdHeader) ~> webApi.newUserRoute ~> check {
        status must beEqualTo(StatusCodes.OK)
      }
    }

    "return an error when unreserving fails" in new LoggedInUserContext {
      checking {
        allowing(publicApi).unreserveWish(userId, friendId, wishId).willReturn(Left(InvalidStatusChange(Deleted, "Cannot unreserve a deleted wish")))
      }

      Delete(s"/${friendId.toString}/wishes/${wishId.toString}/reserve").withHeaders(sessionIdHeader) ~> webApi.newUserRoute ~> check {
        status must beEqualTo(StatusCodes.Conflict)
      }
    }

    "return an error when reserving fails" in new LoggedInUserContext {
      checking {
        allowing(publicApi).reserveWish(userId, friendId, wishId).willReturn(Left(InvalidStatusChange(Deleted, "cannot reserve deleted wish")))
      }

      Post(s"/${friendId.toString}/wishes/${wishId.toString}/reserve").withHeaders(sessionIdHeader) ~> webApi.newUserRoute ~> check {
        status must beEqualTo(StatusCodes.Conflict)
      }
    }

    "return wish by id" in new LoggedInUserContext {
      val wish = Wish(wishId)

      checking {
        allowing(publicApi).wishById(userId, wishId).willReturn(Right(wish))
      }

      Get(s"/me/wishes/$wishId").withHeaders(sessionIdHeader) ~> webApi.newUserRoute ~> check {
        responseAs[Wish] must beEqualTo(wish)
        status must beEqualTo(StatusCodes.OK)
      }
    }

    "return error when wish not found" in new LoggedInUserContext {
      val wish = Wish(wishId)

      checking {
        allowing(publicApi).wishById(userId, wishId).willReturn(Left(WishNotFound(wishId)))
      }

      Get(s"/me/wishes/$wishId").withHeaders(sessionIdHeader) ~> webApi.newUserRoute ~> check {
        status must beEqualTo(StatusCodes.NotFound)
      }
    }

    "send a friend request" in new LoggedInUserContext {
      val request = SendFriendRequest(friendId)

      checking {
        oneOf(publicApi).sendFriendRequest(userId, request).willReturn(Right(()))
      }

      Put(s"/me/friends").withHeaders(sessionIdHeader).withEntity(`application/json`, request.asJson.noSpaces) ~> webApi.newUserRoute ~> check {
        status must beEqualTo(StatusCodes.OK)
      }
    }

    "register a notification id" in new LoggedInUserContext {
      val notificationId = "notification-id"

      checking {
        oneOf(publicApi).setNotificationId(userId, notificationId).willReturn(Right(()))
      }


      Post(s"/me/notifications/id", HttpEntity(formContentType, s"id=$notificationId")).
        withHeaders(sessionIdHeader) ~> webApi.newUserRoute ~> check {
        status must beEqualTo(StatusCodes.OK)
      }
    }

    "mark notification as viewed" in new LoggedInUserContext {
      val notificationId = randomUUID()

      checking {
        oneOf(publicApi).markNotificationAsViewed(userId, notificationId).willReturn(Right(()))
      }

      Post(s"/me/notifications/$notificationId/viewed").withHeaders(sessionIdHeader) ~> webApi.newUserRoute ~> check {
        status must beEqualTo(StatusCodes.OK)
      }
    }

    "Remove friend" in new LoggedInUserContext {
      checking {
        oneOf(publicApi).removeFriend(userId, friendId).willReturn(Right(()))
      }

      Delete(s"/me/friends/$friendId").withHeaders(sessionIdHeader) ~> webApi.newUserRoute ~> check {
        status must beEqualTo(StatusCodes.OK)
      }
    }

    "Search user" in new LoggedInUserContext {
      val query = SearchQuery("abc")
      val results = UserSearchResults(Nil)

      checking {
        oneOf(publicApi).searchUser(userId, query).willReturn(Right(results))
      }

      Post(s"/search", query).withHeaders(sessionIdHeader) ~> webApi.newUserRoute ~> check {
        status must beEqualTo(StatusCodes.OK)
        responseAs[UserSearchResults] must beEqualTo(results)
      }
    }

    "Get friends that have their birthday today" in new LoggedInUserContext {
      val result = FriendBirthdaysResult(List(Friend(friendId)))

      checking {
        oneOf(publicApi).friendsBornToday(userId).willReturn(Right(result))
      }

      Get(s"/me/friends/birthday-today").withHeaders(sessionIdHeader) ~> webApi.newUserRoute ~> check {
        responseAs[FriendBirthdaysResult] must beEqualTo(result)
      }
    }

    "Save user name" in new LoggedInUserContext {
      val firstName = "first-name"
      val lastName = "last-name"
      val setUserName = SetUserName(Option(firstName), Option(lastName))

      checking {
        oneOf(publicApi).setUserName(userId, setUserName).willReturn(Right(()))
      }

      Post("/me/profile/name")
        .withEntity(HttpEntity(ContentTypes.`application/json`, setUserName.asJson.noSpaces))
        .withHeaders(sessionIdHeader) ~> webApi.newUserRoute ~> check {

        handled must beTrue
        status must beEqualTo(StatusCodes.OK)
      }
    }

    "Save gender" in new LoggedInUserContext {
      val setGender = SetGender(Gender.Custom, Option("Non-Binary"), Option(GenderPronoun.Neutral))

      checking {
        oneOf(publicApi).setGender(setGender, userId).willReturn(Right(()))
      }

      val json: String = setGender.asJson.noSpaces
      Post("/me/profile/gender")
        .withEntity(ContentTypes.`application/json`, json)
        .withHeaders(sessionIdHeader) ~> webApi.newUserRoute ~> check {
        handled must beTrue
        status must beEqualTo(StatusCodes.OK)
      }
    }
  }

  trait BaseContext extends Scope {
    val publicApi = mock[PublicApi]
    val managementApi = mock[ManagementApi]

    val webApi = new WebApi(publicApi, managementApi, new MemStateClientRegistry)
  }

  trait LoggedInUserContext extends BaseContext {
    val wishId = randomUUID()
    val sessionId = randomUUID()
    val userId = randomUUID()
    val friendId = randomUUID()
    val requestId = randomUUID()
    val sessionIdHeader = RawHeader(WebApi.sessionIdHeader, sessionId.toString)

    checking {
      allowing(publicApi).userIdForSession(sessionId).willReturn(Some(userId))
    }
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