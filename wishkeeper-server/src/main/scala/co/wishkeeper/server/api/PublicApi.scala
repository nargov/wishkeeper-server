package co.wishkeeper.server.api

import java.io.InputStream
import java.net.URL
import java.nio.file.{Files, Paths}
import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import co.wishkeeper.server.Commands._
import co.wishkeeper.server._
import co.wishkeeper.server.image._
import co.wishkeeper.server.projections._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait PublicApi {
  def userNotificationsFor(sessionId: UUID): List[Notification]

  def userFlagsFor(sessionId: UUID): Flags

  def deleteWish(sessionId: UUID, wishId: UUID): Unit

  def wishListFor(sessionId: UUID): Option[UserWishes]

  def processCommand(command: UserCommand, sessionId: Option[UUID]): Unit

  def connectFacebookUser(command: ConnectFacebookUser): Future[Boolean]

  def userProfileFor(sessionId: UUID): Option[UserProfile]

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
      uploadImage(connection.getInputStream, imageMetadata, wishId, sessionId)
    }
  }

  override def wishListFor(sessionId: UUID): Option[UserWishes] = { //TODO missing tests here
    dataStore.userBySession(sessionId).map { userId =>
      val wishList = replayUser(userId).wishes.values.toList
      UserWishes(wishList.filter(_.status == WishStatus.Active).sortBy(_.creationTime.getMillis).reverse)
    }
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

  override def potentialFriendsFor(facebookAccessToken: String, sessionId: UUID): Option[Future[List[PotentialFriend]]] = {
    for {
      userId <- dataStore.userBySession(sessionId)
      socialData <- userProfileProjection.get(userId).socialData
      facebookId <- socialData.facebookId
    } yield userFriendsProjection.potentialFacebookFriends(facebookId, facebookAccessToken)
  }

  override def userFlagsFor(sessionId: UUID): Flags = {
    withValidSession(sessionId) { replayUser(_).flags }
  }

  private def replayUser(userId: UUID) = User.replay(dataStore.userEvents(userId))

  override def userNotificationsFor(sessionId: UUID): List[Notification] = {
    withValidSession(sessionId) { notificationsProjection.notificationsFor }
  }

  def handleMissingSession(sessionId: UUID) = {
    throw new SessionNotFoundException(Option(sessionId))
  }

  def withValidSession[T](sessionId: UUID)(f: UUID => T) =
    dataStore.
      userBySession(sessionId).
      map(userId => f(userId)).
      getOrElse(handleMissingSession(sessionId))
}
