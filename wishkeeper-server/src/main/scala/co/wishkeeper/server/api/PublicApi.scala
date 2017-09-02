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
import co.wishkeeper.server.projections.{PotentialFriend, UserFriendsProjection, UserProfileProjection}
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait PublicApi {
  def deleteWish(sessionId: UUID, wishId: UUID): Unit

  def wishListFor(sessionId: UUID): Option[UserWishes]

  def processCommand(command: UserCommand, sessionId: Option[UUID]): Unit

  def connectFacebookUser(command: ConnectFacebookUser): Future[Boolean]

  def userProfileFor(sessionId: UUID): Option[UserProfile]

  def potentialFriendsFor(facebookAccessToken: String, sessionId: UUID): Option[Future[List[PotentialFriend]]]

  def incomingFriendRequestSenders(sessionId: UUID): Option[List[UUID]]

  def uploadImage(inputStream: InputStream, imageMetadata: ImageMetadata, wishId: UUID, sessionId: UUID): Try[Unit]

  def uploadImage(url: String, imageMetadata: ImageMetadata, wishId: UUID, sessionId: UUID): Try[Unit]

  def deleteWishImage(sessionId: UUID, wishId: UUID): Unit
}

class DelegatingPublicApi(commandProcessor: CommandProcessor,
                          dataStore: DataStore,
                          facebookConnector: FacebookConnector,
                          incomingFriendRequestsProjection: IncomingFriendRequestsProjection,
                          userProfileProjection: UserProfileProjection,
                          userFriendsProjection: UserFriendsProjection)
                         (implicit actorSystem: ActorSystem, ec: ExecutionContext, am: ActorMaterializer) extends PublicApi {

  private val wishImages = new WishImages(new GoogleCloudStorageImageStore, new ScrimageImageProcessor)

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
      val origFile = uploadedFilePath(imageMetadata)
      FileUtils.copyURLToFile(new URL(url), origFile.toFile) //todo move to adapter
      LoggerFactory.getLogger(getClass).debug(s"Downloaded file from $url to ${origFile.toAbsolutePath.toString}")

      val imageLinks = wishImages.uploadImageAndResizedCopies(imageMetadata, origFile)
      val setWishDetailsEvent = SetWishDetails(Wish(wishId, image = Option(imageLinks)))
      commandProcessor.process(setWishDetailsEvent, Option(sessionId))
    }
  }

  override def wishListFor(sessionId: UUID): Option[UserWishes] = { //TODO missing tests here
    dataStore.userBySession(sessionId).map { userId =>
      val wishList = User.replay(dataStore.userEventsFor(userId)).wishes.values.toList
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

  override def incomingFriendRequestSenders(sessionId: UUID): Option[List[UUID]] = {
    dataStore.userBySession(sessionId).map(incomingFriendRequestsProjection.awaitingApproval)
  }

  override def userProfileFor(sessionId: UUID): Option[UserProfile] = dataStore.userBySession(sessionId).map(userProfileProjection.get)

  override def potentialFriendsFor(facebookAccessToken: String, sessionId: UUID): Option[Future[List[PotentialFriend]]] = {
    for {
      userId <- dataStore.userBySession(sessionId)
      socialData <- userProfileProjection.get(userId).socialData
      facebookId <- socialData.facebookId
    } yield userFriendsProjection.potentialFacebookFriends(facebookId, facebookAccessToken)
  }

}

