package co.wishkeeper.server.api

import java.util.UUID
import java.util.UUID.randomUUID

import cats.data.EitherT
import cats.implicits._
import co.wishkeeper.server.Events._
import co.wishkeeper.server.EventsTestHelper.{EventsList, anEventsListFor}
import co.wishkeeper.server.FriendRequestStatus.{Approved, Ignored}
import co.wishkeeper.server.NotificationsData.{FriendRequestNotification, NotificationData}
import co.wishkeeper.server.UserEventInstant.UserEventInstants
import co.wishkeeper.server.UserTestHelper._
import co.wishkeeper.server.WishStatus.WishStatus
import co.wishkeeper.server._
import co.wishkeeper.server.image.ImageStore
import co.wishkeeper.server.messaging.{EmailProvider, EmailSender, TemplateEngineAdapter}
import co.wishkeeper.server.projections._
import co.wishkeeper.server.search.SimpleScanUserSearchProjection
import co.wishkeeper.server.user._
import co.wishkeeper.server.user.commands._
import co.wishkeeper.server.user.events.history.{HistoryEventInstance, ReceivedWish}
import com.wixpress.common.specs2.JMock
import org.jmock.lib.concurrent.DeterministicExecutor
import org.joda.time.{DateTime, LocalDate}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.Matcher
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

class DelegatingPublicApiTest(implicit ee: ExecutionEnv) extends Specification with JMock {

  "returns the flags for user by the given session" in new LoggedInContext {
    checking {
      allowing(dataStore).userEvents(userId).willReturn(List(
        UserEventInstant(UserConnected(userId, sessionId = sessionId), DateTime.now()),
        UserEventInstant(FacebookFriendsListSeen(), DateTime.now())
      ))
    }

    api.userFlagsFor(sessionId).seenFacebookFriendsList must beTrue
  }

  "throws a SessionNotFoundException if session is not found" in new Context {
    checking {
      allowing(dataStore).userBySession(sessionId).willReturn(None)
    }

    api.userFlagsFor(sessionId) must throwA[SessionNotFoundException]
  }

  "returns user notifications" in new LoggedInContext {
    val notifications = List(Notification(randomUUID(), notificationData))

    checking {
      allowing(notificationsProjection).notificationsFor(userId).willReturn(notifications)
    }

    api.notificationsFor(sessionId) must beEqualTo(UserNotifications(notifications, unread = 1))
  }

  "approve friend request" in new LoggedInContext {
    checking {
      oneOf(commandProcessor).process(ChangeFriendRequestStatus(friendRequestId, Approved), userId)
    }

    api.approveFriendRequest(sessionId, friendRequestId)
  }

  "ignore friend request" in new LoggedInContext {
    checking {
      oneOf(commandProcessor).process(ChangeFriendRequestStatus(friendRequestId, Ignored), userId)
    }

    api.ignoreFriendRequest(sessionId, friendRequestId)
  }

  "return friend profile" in new LoggedInContext {
    val friendName = "Joe"

    checking {
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId).withFriend(friendId, friendRequestId).list)
      allowing(dataStore).userEvents(friendId).willReturn(EventsList(friendId).withName(friendName).list)
    }

    api.userProfileFor(sessionId, friendId) must beRight(UserProfile(name = Some(friendName)))
  }

  "return minimal profile when not friends" in new LoggedInContext {
    val strangerFirstName = "Martin"
    val strangerName = strangerFirstName + " Strange"
    val strangerImage = "expectedImageLink"
    val strangerGender = GenderData.custom("Awesome", GenderPronoun.Neutral)

    checking {
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId).list)
      allowing(dataStore).userEvents(friendId).willReturn(EventsList(friendId).withName(strangerName).withPic(strangerImage)
        .withEvent(UserGenderSet2(strangerGender.gender, strangerGender.customGender, strangerGender.genderPronoun)).list)
    }
    api.userProfileFor(sessionId, friendId) must beRight(
      UserProfile(name = Option(strangerName), picture = Option(strangerImage), genderData = Option(strangerGender)))
  }

  "return friend wishlist" in new LoggedInContext {
    val friendName = "Joe"
    val wishName = "expected wish"

    checking {
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId).withFriend(friendId, friendRequestId).list)
      allowing(dataStore).userEvents(friendId).willReturn(EventsList(friendId).withName(friendName).withWish(wishId, wishName).list)
    }

    api.wishListFor(sessionId, friendId) must beRight(userWishesWith(wishId, wishName))
  }

  "return error when not friends" in new LoggedInContext {
    checking {
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId).list)
    }
    api.wishListFor(sessionId, friendId) must beLeft[ValidationError](NotFriends)
  }

  "return friend friends" in new LoggedInContext {
    val otherFriend = Friend(randomUUID())
    val friends = UserFriends(List(Friend(userId), otherFriend))
    checking {
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId).withFriend(friendId, friendRequestId).list)
      allowing(userFriendsProjection).friendsFor(friendId, userId).willReturn(friends)
    }

    api.friendsListFor(sessionId, friendId) must beRight(UserFriends(List(otherFriend)))
  }

  "unfriend" in new LoggedInContext {
    checking {
      oneOf(commandProcessor).process(RemoveFriend(friendId), userId)
    }
    api.unfriend(sessionId, friendId)
  }

  "grant wish" in new LoggedInContext {
    checking {
      oneOf(commandProcessor).validatedProcess(GrantWish(wishId, Option(friendId)), userId).willReturn(Right(()))
    }

    api.grantWish(userId, wishId, Option(friendId))
  }

  "reserve wish" in new LoggedInContext {
    checking {
      oneOf(commandProcessor).validatedProcess(ReserveWish(userId, wishId), friendId)
    }

    api.reserveWish(userId, friendId, wishId)
  }

  "return active and reserved friend wishes" in new LoggedInContext {
    val activeWish = Wish(randomUUID(), Option("Active Wish"))
    val reservedWish = Wish(randomUUID(), Option("Reserved Wish"), status = WishStatus.Reserved(by = userId))
    val friendEvents = anEventsListFor(friendId).
      withWish(activeWish.id, activeWish.name.get).
      withReservedWish(reservedWish.id, reservedWish.name.get, userId).
      list

    checking {
      allowing(dataStore).userEvents(userId).willReturn(anEventsListFor(userId).withFriend(friendId).list)
      allowing(dataStore).userEvents(friendId).willReturn(friendEvents)
    }

    val result: Either[ValidationError, UserWishes] = api.wishListFor(sessionId, friendId)
    result must beRight
    result.right.get.wishes must contain(allOf(
      aWishWith(activeWish.id, activeWish.name.get),
      aWishWith(reservedWish.id, reservedWish.name.get) and aWishWithStatus(reservedWish.status)))
  }

  "unreserve wish" in new LoggedInContext {
    checking {
      oneOf(commandProcessor).validatedProcess(UnreserveWish(wishId), friendId)
    }

    api.unreserveWish(userId, friendId, wishId)
  }

  "return wish by id" in new LoggedInContext {
    val wishName = "name"
    checking {
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId).withWish(wishId, wishName).list)
    }

    api.wishById(userId, wishId) must beRight(aWishWith(wishId, wishName))
  }

  "return wish not found if doesn't exist" in new LoggedInContext {
    checking {
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId).list)
    }

    api.wishById(userId, wishId) must beLeft[Error](WishNotFound(wishId))
  }

  "delete wish" in new LoggedInContext {
    checking {
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId).withWish(wishId, "expected wish").list)
      oneOf(commandProcessor).validatedProcess(DeleteWish(wishId), userId).willReturn(Right(()))
    }

    api.deleteWish(userId, wishId) must beRight(())
  }

  "sends a friend request" in new LoggedInContext {
    val friendRequest = SendFriendRequest(friendId)

    checking {
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId).list)
      oneOf(commandProcessor).validatedProcess(friendRequest, userId).willReturn(Right(()))
    }

    api.sendFriendRequest(userId, friendRequest) must beRight(())
  }

  "accepts existing friend request instead of issuing a new one" in new LoggedInContext {
    val userEvents = EventsList(userId).withIncomingFriendRequest(friendId, friendRequestId).list

    checking {
      allowing(dataStore).userEvents(userId).willReturn(userEvents)
      oneOf(commandProcessor).validatedProcess(ChangeFriendRequestStatus(friendRequestId, Approved), userId).willReturn(Right(()))
    }

    api.sendFriendRequest(userId, SendFriendRequest(friendId))
  }

  "sets a notification ID" in new LoggedInContext {
    val notificationId = "id"

    checking {
      oneOf(commandProcessor).validatedProcess(SetDeviceNotificationId(notificationId), userId).willReturn(Right(()))
    }

    api.setNotificationId(userId, notificationId)
  }

  "marks a notification as read" in new LoggedInContext {
    val notificationId = randomUUID()

    checking {
      oneOf(commandProcessor).validatedProcess(MarkNotificationViewed(notificationId), userId).willReturn(Right(()))
    }

    api.markNotificationAsViewed(userId, notificationId)
  }

  "removes friend" in new LoggedInContext {
    checking {
      oneOf(commandProcessor).validatedProcess(RemoveFriend(friendId), userId).willReturn(Right(()))
    }

    api.removeFriend(userId, friendId)
  }

  // TODO: do this as an IT, or refactor to use file system adapter to be able to unit test.

  //  "upload user profile image" in new LoggedInContext {
  //    val imageMetadata = ImageMetadata("image/jpeg", "file-name", 1, 1)
  //    val inputStream = new ByteArrayInputStream(Array[Byte](1))
  //    val base = "http://www.example.com"
  //
  //    checking {
  //      allowing(userImageStore).imageLinkBase.willReturn(base)
  //      oneOf(userImageStore).save(having(any[ImageData]), having(===(imageMetadata.fileName + ".full")))
  //      oneOf(commandProcessor).validatedProcess(SetUserPicture(base + imageMetadata.fileName + ".small"), userId)
  //    }
  //
  //    api.uploadProfileImage(inputStream, imageMetadata, userId)
  //  }

  "return friend wish" in new LoggedInContext {
    val wishName = "The Wish"
    checking {
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId).withFriend(friendId).list)
      allowing(dataStore).userEvents(friendId).willReturn(EventsList(friendId).withWish(wishId, wishName).list)
    }

    api.wishById(userId, friendId, wishId) must beRight(aWishWith(wishId, wishName))
  }

  "return error if wish belongs to user who is not a friend" in new LoggedInContext {
    val wishName = "The Wish"
    checking {
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId).list)
    }

    api.wishById(userId, friendId, wishId) must beLeft[Error](NotFriends)
  }

  "return friend history" in new LoggedInContext {
    val eventInstance = HistoryEventInstance(friendId, wishId, DateTime.now(), ReceivedWish(wishId, userId, "Joe", "Wish"))

    checking {
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId).withFriend(friendId).list)
      oneOf(userHistoryProjection).friendHistory(friendId).willReturn(eventInstance :: Nil)
    }

    api.historyFor(userId, friendId) must beRight(contain(eventInstance))
  }

  "return error if not friends" in new LoggedInContext {
    checking {
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId).list)
    }

    api.historyFor(userId, friendId) must beLeft[Error](NotFriends)
  }

  "verify email" in new Context {
    val verificationToken = randomUUID()
    checking {
      oneOf(dataStore).verifyEmailToken(verificationToken).willReturn(Right(VerificationToken(verificationToken, "email", userId)))
      oneOf(commandProcessor).validatedProcess(MarkEmailVerified, userId).willReturn(Right(()))
    }

    api.verifyEmail(verificationToken)
  }

  "resend email" in new Context {
    val idToken = "token"
    val email = "email"
    val emailContent = "email content"

    checking {
      oneOf(firebaseAuth).validate(idToken).willReturn(Right(EmailAuthData(email)))
      allowing(dataStore).userIdByEmail(email).willReturn(Option(userId))
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId)
        .withFirstName("firstName")
        .withEvent(EmailConnectStarted(userId))
        .list)
      oneOf(dataStore).saveVerificationToken(having(any[VerificationToken])).willReturn(Right(true))
      oneOf(templateEngineAdapter).process(having(===(EmailSender.verificationEmailTemplate)), having(any)).willReturn(Success(emailContent))
      oneOf(emailProvider).sendEmail(having(===(email)), having(any), having(===(EmailSender.verificationEmailSubject)), having(any[String]),
        having(===(emailContent))).willReturn(Future.successful(Right(())))
    }

    api.resendVerificationEmail(email, idToken)
    executor.runUntilIdle()
  }

  "return friends with upcoming birthdays in home data" in new Context {
    val friendEvents: EitherT[Future, Error, List[UserEventInstant[_ <: Events.UserEvent]]] = EitherT.right(Future.successful(EventsList(friendId)
      .withBirthday(LocalDate.now().plusDays(1).toString("MM/dd/yyyy"))
      .withWish(wishId, "Wish").list))

    checking {
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId).withFriend(friendId).list)
      allowing(dataStore).userEventsAsync(friendId).willReturn(friendEvents)
    }

    val result = apiWithStoreOnly.homeScreenData(userId).value
    result must beRight(homeScreenDataWithBirthdayFriend).await(20, 20.millis)
  }

  "return friends with recently changed wishlist in home data" in new Context {
    val otherFriendId: UUID = randomUUID()
    val friendEvents: EitherT[Future, Error, UserEventInstants] = EitherT.right(Future.successful(EventsList(friendId)
      .withName("FRIEND").withWish(wishId, "Wish").list))
    val otherFriendEvents: EitherT[Future, Error, UserEventInstants] = EitherT.right(Future.successful(EventsList(otherFriendId)
      .withName("OTHER FRIEND").withWish(randomUUID(), "Some Wish", DateTime.now().minusDays(40)).list))

    checking {
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId).withFriend(friendId).withFriend(otherFriendId).list)
      allowing(dataStore).userEventsAsync(friendId).willReturn(friendEvents)
      allowing(dataStore).userEventsAsync(otherFriendId).willReturn(otherFriendEvents)
    }

    val result: Future[Either[Error, HomeScreenData]] = apiWithStoreOnly.homeScreenData(userId).value
    result must beRight(homeScreenDataWithUpdatedFriendWishlist(friendId)).await(20, 20.millis) and
      not(beRight(homeScreenDataWithUpdatedFriendWishlist(otherFriendId)).await(20, 20.millis))
  }

  "return friend with upcoming birthday but not in recently changed wishlist" in new Context {
    val friendEvents: EitherT[Future, Error, UserEventInstants] = EitherT.right(Future.successful(EventsList(friendId)
      .withWish(wishId, "Wish")
      .withBirthday(LocalDate.now().plusDays(1).minusYears(20).toString("MM/dd/yyyy"))
      .list))

    checking {
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId).withFriend(friendId).list)
      allowing(dataStore).userEventsAsync(friendId).willReturn(friendEvents)
    }

    val result: Future[Either[Error, HomeScreenData]] = apiWithStoreOnly.homeScreenData(userId).value
    result must beRight(homeScreenDataWithBirthdayFriend).await(20, 20.millis) and
      not(beRight(homeScreenDataWithUpdatedFriendWishlist(friendId))).await(20, 20.millis)
  }

  "return friends with recently changed wishlist only if have wishes" in new Context {
    val now: DateTime = DateTime.now()
    val otherFriendId: UUID = randomUUID()
    val friendEvents: EitherT[Future, Error, UserEventInstants] = EitherT.right(Future.successful(EventsList(friendId)
      .withWish(wishId, "Wish", now.minusDays(5)).list))
    val deletedWish: UUID = randomUUID()
    val otherFriendEvents: EitherT[Future, Error, UserEventInstants] = EitherT.right(Future.successful(EventsList(otherFriendId)
      .withWish(deletedWish, "SHOULD NOT BE HERE", now.minusDays(20))
      .withEvent(WishDeleted(deletedWish), now.minusDays(10)).list))

    checking {
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId).withFriend(friendId).withFriend(otherFriendId).list)
      allowing(dataStore).userEventsAsync(friendId).willReturn(friendEvents)
      allowing(dataStore).userEventsAsync(otherFriendId).willReturn(otherFriendEvents)
    }

    val result: Future[Either[Error, HomeScreenData]] = apiWithStoreOnly.homeScreenData(userId).value
    result must beRight(homeScreenDataWithUpdatedFriendWishlist(friendId)).await(20, 20.millis) and
      not(beRight(homeScreenDataWithUpdatedFriendWishlist(otherFriendId)).await(20, 20.millis))
  }

  "return friends with recently changed wishlist ordered by wishlist update date" in new Context {
    val now: DateTime = DateTime.now()
    val otherFriendId = randomUUID()
    val yetAnotherFriendId = randomUUID()
    val friendEvents: EitherT[Future, Error, UserEventInstants] = EitherT.right(Future.successful(EventsList(friendId)
      .withWish(wishId, "Wish", now.minusDays(5)).list))
    val deletedWish: UUID = randomUUID()
    val otherFriendEvents: EitherT[Future, Error, UserEventInstants] = EitherT.right(Future.successful(EventsList(otherFriendId)
      .withWish(deletedWish, "Another wish", now.minusDays(10)).list))
    val yetAnotherFriendEvents: EitherT[Future, Error, UserEventInstants] = EitherT.right(Future.successful(EventsList(yetAnotherFriendId)
      .withWish(randomUUID(), "Yet another wish", now.minusDays(3)).list))

    checking {
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId).withFriend(friendId).withFriend(otherFriendId)
        .withFriend(yetAnotherFriendId).list)
      allowing(dataStore).userEventsAsync(friendId).willReturn(friendEvents)
      allowing(dataStore).userEventsAsync(otherFriendId).willReturn(otherFriendEvents)
      allowing(dataStore).userEventsAsync(yetAnotherFriendId).willReturn(yetAnotherFriendEvents)
    }

    val result: Future[Either[Error, HomeScreenData]] = apiWithStoreOnly.homeScreenData(userId).value
    result must beRight(haveUpdatedWishlistsForFriends(List(yetAnotherFriendId, friendId, otherFriendId))).await(20, 20.millis)
  }

  def haveUpdatedWishlistsForFriends(friendsIds: List[UUID]): Matcher[HomeScreenData] = (data: HomeScreenData) =>
    (data.updatedWishlists.friends.map(_.friend.userId) == friendsIds, "Friend IDs do not match ids of updated wishlist friends")

  def homeScreenDataWithUpdatedFriendWishlist(friendId: UUID): Matcher[HomeScreenData] = (data: HomeScreenData) =>
    (data.updatedWishlists.friends.exists(_.friend.userId == friendId), "Does not contain a friends with an updated wishlist")

  def homeScreenDataWithBirthdayFriend: Matcher[HomeScreenData] = (data: HomeScreenData) =>
    (data.birthdays.friends.exists(_.wishlist.exists(_.name.contains("Wish"))), "Does not contain a friend with a wish with upcoming birthday")

  def userWishesWith(wishId: UUID, wishName: String): Matcher[UserWishes] = contain(aWishWith(wishId, wishName)) ^^ {
    (_: UserWishes).wishes
  }

  def aWishWith(id: UUID, name: String): Matcher[Wish] = (wish: Wish) =>
    (wish.id == id && wish.name.isDefined && wish.name.get == name, s"Wish $wish does not match name $name and id $id")

  def aWishWithStatus(status: WishStatus): Matcher[Wish] = (wish: Wish) => (wish.status == status, s"Wish $wish status is not $status")


  trait Context extends Scope {
    val sessionId: UUID = randomUUID()
    val userId: UUID = randomUUID()
    val dataStore: DataStore = mock[DataStore]
    val notificationsProjection = mock[NotificationsProjection]
    val commandProcessor = mock[CommandProcessor]
    val userFriendsProjection: UserFriendsProjection = mock[UserFriendsProjection]
    val searchProjection = new SimpleScanUserSearchProjection(dataStore)
    val userProfileProjection: UserProfileProjection = new ReplayingUserProfileProjection(dataStore)
    val userHistoryProjection = mock[UserHistoryProjection]
    val userImageStore = mock[ImageStore]
    val googleAuth = mock[GoogleAuthAdapter]
    val firebaseAuth = mock[EmailAuthProvider]
    val emailProvider = mock[EmailProvider]
    val templateEngineAdapter = mock[TemplateEngineAdapter]
    val emailSender = new EmailSender(emailProvider, templateEngineAdapter)
    val executor = new DeterministicExecutor

    val api: PublicApi = new DelegatingPublicApi(
      commandProcessor,
      dataStore,
      null,
      userProfileProjection,
      userFriendsProjection,
      notificationsProjection,
      searchProjection,
      null,
      userImageStore,
      userHistoryProjection,
      googleAuth,
      firebaseAuth,
      emailSender
    )(null, ExecutionContext.fromExecutor(executor), null)
    val friendId: UUID = randomUUID()
    val friendRequestId = randomUUID()
    val notificationData = FriendRequestNotification(friendId, friendRequestId)
    val wishId = randomUUID()

    val apiWithStoreOnly = new DelegatingPublicApi(null, dataStore, null, null,
      new EventBasedUserFriendsProjection(null, null, null, dataStore),
      null, null, null, null, null, null, null,
      null)(null, ee.executionContext, null)
  }

  trait LoggedInContext extends Context {
    checking {
      allowing(dataStore).userBySession(sessionId).willReturn(Option(userId))
    }
  }

  def aNotificationWith(data: NotificationData, viewed: Boolean): Matcher[Notification] = (notification: Notification) =>
    (notification.data == data && viewed == notification.viewed,
      s"$notification does not match $data and viewed[$viewed]")
}
