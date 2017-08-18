package co.wishkeeper.server.api

import java.io.InputStream
import java.net.URL
import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import co.wishkeeper.server.Commands._
import co.wishkeeper.server.Server.mediaServerBase
import co.wishkeeper.server.image.{ImageData, ImageMetadata, ImageStore}
import co.wishkeeper.server.projections.{PotentialFriend, UserFriendsProjection, UserProfileProjection}
import co.wishkeeper.server._
import org.apache.commons.io.FileUtils

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
                          imageProcessor: ImageProcessor,
                          imageStore: ImageStore,
                          dataStore: DataStore,
                          facebookConnector: FacebookConnector,
                          incomingFriendRequestsProjection: IncomingFriendRequestsProjection,
                          userProfileProjection: UserProfileProjection,
                          userFriendsProjection: UserFriendsProjection)
                         (implicit actorSystem: ActorSystem, ec: ExecutionContext, am: ActorMaterializer) extends PublicApi {
  override def deleteWish(sessionId: UUID, wishId: UUID): Unit = commandProcessor.process(DeleteWish(wishId), Option(sessionId))

  override def deleteWishImage(sessionId: UUID, wishId: UUID): Unit = commandProcessor.process(DeleteWishImage(wishId), Option(sessionId))

  override def uploadImage(inputStream: InputStream, imageMetadata: ImageMetadata, wishId: UUID, sessionId: UUID): Try[Unit] = {
    Try {
      val origFile = Paths.get(ImageProcessor.tempDir.toString, imageMetadata.fileName)
      Files.copy(inputStream, origFile)

      uploadAndSetImages(imageMetadata, wishId, sessionId, origFile)
    }
  }

  override def uploadImage(url: String, imageMetadata: ImageMetadata, wishId: UUID, sessionId: UUID): Try[Unit] = {
    Try {
      val origFile = Paths.get(ImageProcessor.tempDir.toString, imageMetadata.fileName)
      FileUtils.copyURLToFile(new URL(url), origFile.toFile)

      uploadAndSetImages(imageMetadata, wishId, sessionId, origFile)
    }
  }

  private def uploadAndSetImages(imageMetadata: ImageMetadata, wishId: UUID, sessionId: UUID, origFile: Path) = {
    val sizeExtensions = List(
      (".full", imageMetadata.width),
      (".fhd", 1080),
      (".hfhd", 540),
      (".qfhd", 270)
    )

    val resizedImages: List[ImageLink] = sizeExtensions.filter { case (_, width) =>
      width <= imageMetadata.width
    }.map { case (ext, width) =>
      val file = if (width == imageMetadata.width)
        imageProcessor.compress(origFile, ext)
      else
        imageProcessor.resizeToWidth(origFile, ext, width)
      val fileName = file.getFileName.toString
      val (_, height) = imageProcessor.dimensions(file)
      imageStore.save(ImageData(Files.newInputStream(file), ContentTypes.jpeg), fileName)
      Files.deleteIfExists(file)
      ImageLink(s"$mediaServerBase/$fileName", width, height, ContentTypes.jpeg)
    }

    Files.deleteIfExists(origFile)

    processCommand(SetWishDetails(Wish(wishId, image = Option(ImageLinks(resizedImages)))), Option(sessionId))
  }

  override def wishListFor(userId: UUID): Option[UserWishes] = { //TODO missing tests here - need to move this logic so that can unit test
    dataStore.userBySession(userId).map { userId =>
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

object ContentTypes {
  val jpeg = "image/jpeg"
}