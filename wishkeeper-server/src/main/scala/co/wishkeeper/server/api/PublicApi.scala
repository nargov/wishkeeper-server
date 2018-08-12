package co.wishkeeper.server.api

import java.io.InputStream
import java.net.URL
import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import co.wishkeeper.server.FriendRequestStatus.{Approved, Ignored}
import co.wishkeeper.server.WishStatus.{Active, Reserved, WishStatus}
import co.wishkeeper.server._
import co.wishkeeper.server.image._
import co.wishkeeper.server.projections._
import co.wishkeeper.server.search.{SearchQuery, UserSearchProjection, UserSearchResults}
import co.wishkeeper.server.user.commands._
import co.wishkeeper.server.user.{NotFriends, ValidationError, WishNotFound}
import com.google.common.net.UrlEscapers

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait PublicApi {

  def removeFriend(userId: UUID, friendId: UUID): Either[Error, Unit]

  def markNotificationAsViewed(userId: UUID, notificationId: UUID): Either[Error, Unit]

  def setNotificationId(userId: UUID, notificationId: String): Either[Error, Unit]

  def sendFriendRequest(userId: UUID, request: SendFriendRequest): Either[Error, Unit]

  def wishById(userId: UUID, wishId: UUID): Either[Error, Wish]

  def unreserveWish(userId: UUID, friendId: UUID, wishId: UUID): Either[Error, Unit]

  def reserveWish(userId: UUID, friendId: UUID, wishId: UUID): Either[Error, Unit]

  def grantWish(userId: UUID, wishId: UUID, granterId: Option[UUID] = None): Either[Error, Unit]

  def unfriend(sessionId: UUID, friendId: UUID): Either[ValidationError, Unit]

  def markAllNotificationsViewed(sessionId: UUID): Unit

  def friendsListFor(sessionId: UUID, friendId: UUID): Either[ValidationError, UserFriends]

  def friendsListFor(sessionId: UUID): UserFriends

  def ignoreFriendRequest(sessionId: UUID, reqId: UUID): Unit

  def approveFriendRequest(sessionId: UUID, reqId: UUID): Unit

  def notificationsFor(sessionId: UUID): UserNotifications

  def userFlagsFor(sessionId: UUID): Flags

  def deleteWish(userId: UUID, wishId: UUID): Either[Error, Unit]

  def wishListFor(sessionId: UUID): Option[UserWishes]

  def wishListFor(sessionId: UUID, friendId: UUID): Either[ValidationError, UserWishes]

  def processCommand(command: UserCommand, sessionId: Option[UUID]): Unit

  def processCommand[C <: UserCommand](command: C, userId: UUID)(implicit validator: UserCommandValidator[C]): Either[Error, Unit]

  def connectFacebookUser(command: ConnectFacebookUser): Future[Boolean]

  def userProfileFor(sessionId: UUID): Option[UserProfile]

  def userProfileFor(sessionId: UUID, friendId: UUID): Either[ValidationError, UserProfile]

  def potentialFriendsFor(facebookAccessToken: String, sessionId: UUID): Option[Future[List[PotentialFriend]]]

  def uploadImage(inputStream: InputStream, imageMetadata: ImageMetadata, wishId: UUID, sessionId: UUID): Try[Unit]

  def uploadImage(url: String, imageMetadata: ImageMetadata, wishId: UUID, sessionId: UUID): Try[Unit]

  def deleteWishImage(sessionId: UUID, wishId: UUID): Unit

  def userIdForSession(sessionId: UUID): Option[UUID]

  def searchUser(userId: UUID, query: SearchQuery): Either[Error, UserSearchResults]
}

class DelegatingPublicApi(commandProcessor: CommandProcessor,
                          dataStore: DataStore,
                          facebookConnector: FacebookConnector,
                          userProfileProjection: UserProfileProjection,
                          userFriendsProjection: UserFriendsProjection,
                          notificationsProjection: NotificationsProjection,
                          searchProjection: UserSearchProjection,
                          imageStore: ImageStore)
                         (implicit actorSystem: ActorSystem, ec: ExecutionContext, am: ActorMaterializer) extends PublicApi {

  private val wishImages = new WishImages(imageStore, new ScrimageImageProcessor)

  override def deleteWish(userId: UUID, wishId: UUID): Either[Error, Unit] = commandProcessor.validatedProcess(DeleteWish(wishId), userId)

  override def deleteWishImage(sessionId: UUID, wishId: UUID): Unit =
    withValidSession(sessionId)(commandProcessor.process(DeleteWishImage(wishId), _))

  private def uploadedFilePath(imageMetadata: ImageMetadata): Path = Paths.get(ImageProcessor.tempDir.toString, imageMetadata.fileName)

  override def uploadImage(inputStream: InputStream, imageMetadata: ImageMetadata, wishId: UUID, sessionId: UUID): Try[Unit] = {
    withValidSession(sessionId) { userId =>
      Try {
        val origFile = uploadedFilePath(imageMetadata)
        Files.copy(inputStream, origFile) //todo move to adapter

        val imageLinks = wishImages.uploadImageAndResizedCopies(imageMetadata, origFile)
        val setWishDetailsEvent = SetWishDetails(Wish(wishId, image = Option(imageLinks)))
        commandProcessor.process(setWishDetailsEvent, userId)
      }
    }
  }

  override def uploadImage(url: String, imageMetadata: ImageMetadata, wishId: UUID, sessionId: UUID): Try[Unit] = {
    Try {
      val escapedUrl = UrlEscapers.urlFragmentEscaper().escape(url)
      val connection = new URL(escapedUrl).openConnection()
      connection.setRequestProperty("User-Agent",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.113 Safari/537.36")
      connection.connect()
      connection
    }.flatMap(con => uploadImage(con.getInputStream, imageMetadata, wishId, sessionId))
  }

  override def wishListFor(sessionId: UUID): Option[UserWishes] = {
    dataStore.userBySession(sessionId).map { userId =>
      UserWishes(activeWishesByDate(replayUser(userId)))
    }
  }

  private val isShownInWishlist: WishStatus => Boolean = {
    case Active | Reserved(_) => true
    case _ => false
  }

  private def activeWishesByDate(user: User) = {
    val wishList = user.wishes.values.toList
    wishList.
      filter(w => isShownInWishlist(w.status)).
      sortBy(_.creationTime.getMillis).
      reverse
  }

  override def wishListFor(sessionId: UUID, friendId: UUID): Either[ValidationError, UserWishes] = withValidSession(sessionId) { userId =>
    val user = replayUser(userId)
    if (user.hasFriend(friendId))
      Right(UserWishes(activeWishesByDate(replayUser(friendId))))
    else
      Left(NotFriends)
  }

  override def processCommand(command: UserCommand, sessionId: Option[UUID]): Unit = commandProcessor.process(command, sessionId)

  override def connectFacebookUser(command: ConnectFacebookUser): Future[Boolean] = {
    val eventualIsValid = facebookConnector.isValid(command.authToken)
    eventualIsValid.map { isValid =>
      if (isValid) commandProcessor.process(command)
      isValid
    }
  }

  override def userProfileFor(sessionId: UUID): Option[UserProfile] = dataStore.userBySession(sessionId).map(userProfileProjection.get)

  override def userProfileFor(sessionId: UUID, friendId: UUID): Either[ValidationError, UserProfile] = {
    withValidSession(sessionId) { userId =>
      val user = User.replay(dataStore.userEvents(userId))
      if (user.hasFriend(friendId))
        Right(userProfileProjection.get(friendId))
      else
        Right(userProfileProjection.strangerProfile(friendId))
    }
  }

  override def potentialFriendsFor(facebookAccessToken: String, sessionId: UUID): Option[Future[List[PotentialFriend]]] = {
    withValidSession(sessionId) { userId =>
      Option(userFriendsProjection.potentialFacebookFriends(userId, facebookAccessToken))
    }
  }

  override def userFlagsFor(sessionId: UUID): Flags = {
    withValidSession(sessionId) {
      replayUser(_).flags
    }
  }

  private def replayUser(userId: UUID): User = User.replay(dataStore.userEvents(userId))

  override def notificationsFor(sessionId: UUID): UserNotifications = {
    withValidSession(sessionId) { userId =>
      val notifications = notificationsProjection.notificationsFor(userId)
      UserNotifications(notifications, notifications.count(!_.viewed))
    }
  }

  def handleMissingSession(sessionId: UUID) = throw new SessionNotFoundException(Option(sessionId))

  def withValidSession[T](sessionId: UUID)(f: UUID => T): T =
    dataStore.
      userBySession(sessionId).
      map(userId => f(userId)).
      getOrElse(handleMissingSession(sessionId))

  override def approveFriendRequest(sessionId: UUID, reqId: UUID): Unit = {
    withValidSession(sessionId) {
      commandProcessor.process(ChangeFriendRequestStatus(reqId, Approved), _)
    }
  }

  override def ignoreFriendRequest(sessionId: UUID, reqId: UUID): Unit = withValidSession(sessionId) {
    commandProcessor.process(ChangeFriendRequestStatus(reqId, Ignored), _)
  }

  override def friendsListFor(sessionId: UUID): UserFriends = withValidSession(sessionId)(userFriendsProjection.friendsFor)

  override def friendsListFor(sessionId: UUID, friendId: UUID): Either[ValidationError, UserFriends] = withValidSession(sessionId) { userId =>
    val user = User.replay(dataStore.userEvents(userId))
    Right(userFriendsProjection.friendsFor(friendId, user.id).excluding(user.id))
  }

  override def markAllNotificationsViewed(sessionId: UUID): Unit = withValidSession(sessionId) {
    commandProcessor.process(MarkAllNotificationsViewed, _)
  }

  override def unfriend(sessionId: UUID, friendId: UUID): Either[ValidationError, Unit] = withValidSession(sessionId) { userId =>
    commandProcessor.process(RemoveFriend(friendId), userId)
    Right(())
  }

  override def userIdForSession(sessionId: UUID): Option[UUID] = dataStore.userBySession(sessionId)

  override def grantWish(userId: UUID, wishId: UUID, granterId: Option[UUID] = None): Either[Error, Unit] = {
    commandProcessor.validatedProcess(GrantWish(wishId, granterId), userId)
  }

  override def reserveWish(userId: UUID, friendId: UUID, wishId: UUID): Either[Error, Unit] = {
    commandProcessor.validatedProcess(ReserveWish(userId, wishId), friendId)
  }

  //TODO check if user was the original reserver
  override def unreserveWish(userId: UUID, friendId: UUID, wishId: UUID): Either[Error, Unit] = {
    commandProcessor.validatedProcess(UnreserveWish(wishId), friendId)
  }

  override def wishById(userId: UUID, wishId: UUID): Either[Error, Wish] = {
    val wishes = replayUser(userId).wishes
    Either.cond(wishes.contains(wishId), wishes(wishId), WishNotFound(wishId))
  }

  override def processCommand[C <: UserCommand](command: C, userId: UUID)(implicit validator: UserCommandValidator[C]): Either[Error, Unit] =
    commandProcessor.validatedProcess(command, userId)

  override def sendFriendRequest(userId: UUID, request: SendFriendRequest): Either[Error, Unit] = {
    replayUser(userId).friendRequestId(request.friendId).
      fold(commandProcessor.validatedProcess(request, userId))(reqId =>
        commandProcessor.validatedProcess(ChangeFriendRequestStatus(reqId, Approved), userId))
  }

  override def setNotificationId(userId: UUID, notificationId: String): Either[Error, Unit] =
    commandProcessor.validatedProcess(SetDeviceNotificationId(notificationId), userId)

  override def markNotificationAsViewed(userId: UUID, notificationId: UUID): Either[Error, Unit] =
    commandProcessor.validatedProcess(MarkNotificationViewed(notificationId), userId)

  override def removeFriend(userId: UUID, friendId: UUID): Either[Error, Unit] = commandProcessor.validatedProcess(RemoveFriend(friendId), userId)

  override def searchUser(userId: UUID, search: SearchQuery): Either[Error, UserSearchResults] = Right(searchProjection.byName(userId, search.query))
}
