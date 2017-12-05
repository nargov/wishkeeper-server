package co.wishkeeper.server.api

import java.io.InputStream
import java.net.URL
import java.nio.file.{Files, Paths}
import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import co.wishkeeper.server.Commands._
import co.wishkeeper.server.FriendRequestStatus.{Approved, Ignored}
import co.wishkeeper.server._
import co.wishkeeper.server.image._
import co.wishkeeper.server.projections._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait PublicApi {

  def markAllNotificationsViewed(sessionId: UUID): Unit

  def friendsListFor(sessionId: UUID, friendId: UUID): Either[ValidationError, UserFriends]

  def friendsListFor(sessionId: UUID): UserFriends

  def ignoreFriendRequest(sessionId: UUID, reqId: UUID): Unit

  def approveFriendRequest(sessionId: UUID, reqId: UUID): Unit

  def notificationsFor(sessionId: UUID): UserNotifications

  def userFlagsFor(sessionId: UUID): Flags

  def deleteWish(sessionId: UUID, wishId: UUID): Unit

  def wishListFor(sessionId: UUID): Option[UserWishes]

  def wishListFor(sessionId: UUID, friendId: UUID): Either[ValidationError, UserWishes]

  def processCommand(command: UserCommand, sessionId: Option[UUID]): Unit

  def connectFacebookUser(command: ConnectFacebookUser): Future[Boolean]

  def userProfileFor(sessionId: UUID): Option[UserProfile]

  def userProfileFor(sessionId: UUID, friendId: UUID): Either[ValidationError, UserProfile]

  def potentialFriendsFor(facebookAccessToken: String, sessionId: UUID): Option[Future[List[PotentialFriend]]]

  def uploadImage(inputStream: InputStream, imageMetadata: ImageMetadata, wishId: UUID, sessionId: UUID): Try[Unit]

  def uploadImage(url: String, imageMetadata: ImageMetadata, wishId: UUID, sessionId: UUID): Try[Unit]

  def deleteWishImage(sessionId: UUID, wishId: UUID): Unit
}

class DelegatingPublicApi(commandProcessor: CommandProcessor,
                          dataStore: DataStore,
                          facebookConnector: FacebookConnector,
                          incomingFriendRequestsProjection: IncomingFriendRequestsProjection,
                          userProfileProjection: UserProfileProjection,
                          userFriendsProjection: UserFriendsProjection,
                          notificationsProjection: NotificationsProjection,
                          imageStore: ImageStore)
                         (implicit actorSystem: ActorSystem, ec: ExecutionContext, am: ActorMaterializer) extends PublicApi {

  private val wishImages = new WishImages(imageStore, new ScrimageImageProcessor)

  override def deleteWish(sessionId: UUID, wishId: UUID): Unit = commandProcessor.process(DeleteWish(wishId), Option(sessionId))

  override def deleteWishImage(sessionId: UUID, wishId: UUID): Unit = commandProcessor.process(DeleteWishImage(wishId), Option(sessionId))

  private def uploadedFilePath(imageMetadata: ImageMetadata) = Paths.get(ImageProcessor.tempDir.toString, imageMetadata.fileName)

  override def uploadImage(inputStream: InputStream, imageMetadata: ImageMetadata, wishId: UUID, sessionId: UUID): Try[Unit] = {
    Try {
      val origFile = uploadedFilePath(imageMetadata)
      Files.copy(inputStream, origFile) //todo move to adapter

      val imageLinks = wishImages.uploadImageAndResizedCopies(imageMetadata, origFile)
      val setWishDetailsEvent = SetWishDetails(Wish(wishId, image = Option(imageLinks)))
      commandProcessor.process(setWishDetailsEvent, Option(sessionId))
    }
  }

  override def uploadImage(url: String, imageMetadata: ImageMetadata, wishId: UUID, sessionId: UUID): Try[Unit] = {
    Try {
      val connection = new URL(url).openConnection()
      connection.setRequestProperty("User-Agent",
        "Mozilla/5.0 (Windows NT 6.4; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2225.0 Safari/537.36")
      connection.connect()
      connection
    }.flatMap(con => uploadImage(con.getInputStream, imageMetadata, wishId, sessionId))
  }

  override def wishListFor(sessionId: UUID): Option[UserWishes] = {
    dataStore.userBySession(sessionId).map { userId =>
      UserWishes(activeWishesByDate(replayUser(userId)))
    }
  }

  private def activeWishesByDate(user: User) = {
    val wishList = user.wishes.values.toList
    wishList.
      filter(_.status == WishStatus.Active).
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

  private def replayUser(userId: UUID) = User.replay(dataStore.userEvents(userId))

  override def notificationsFor(sessionId: UUID): UserNotifications = {
    withValidSession(sessionId) { userId =>
      val notifications = notificationsProjection.notificationsFor(userId)
      UserNotifications(notifications, notifications.count(!_.viewed))
    }
  }

  def handleMissingSession(sessionId: UUID) = throw new SessionNotFoundException(Option(sessionId))

  def withValidSession[T](sessionId: UUID)(f: UUID => T) =
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
}

sealed trait ValidationError {
  val msg: String
}

case object NotFriends extends ValidationError {
  override val msg: String = "Users are not friends"
}
