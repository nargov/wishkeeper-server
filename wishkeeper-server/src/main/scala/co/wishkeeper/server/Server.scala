package co.wishkeeper.server

import java.io.InputStream
import java.nio.file.{Files, Paths}
import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import co.wishkeeper.json._
import co.wishkeeper.server.Commands.{ConnectFacebookUser, SetWishDetails, UserCommand}
import co.wishkeeper.server.Server.mediaServerBase
import co.wishkeeper.server.api.{ManagementApi, PublicApi}
import co.wishkeeper.server.image.{GoogleCloudStorageImageStore, ImageData, ImageMetadata}
import co.wishkeeper.server.projections._
import com.typesafe.config.ConfigFactory

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.Try


class WishkeeperServer() extends PublicApi with ManagementApi {
  private val dataStore = new CassandraDataStore
  private val userIdByFacebookIdProjection = new DataStoreUserIdByFacebookIdProjection(dataStore)
  private val incomingFriendRequestsProjection = new DataStoreIncomingFriendRequestsProjection(dataStore)
  private val commandProcessor: CommandProcessor = new UserCommandProcessor(dataStore,
    List(userIdByFacebookIdProjection, incomingFriendRequestsProjection))
  private val userProfileProjection: UserProfileProjection = new ReplayingUserProfileProjection(dataStore)

  private implicit val system = ActorSystem("web-api")
  private implicit val materializer = ActorMaterializer()
  private implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  private val config = ConfigFactory.load("wishkeeper")
  private val facebookConnector: FacebookConnector = new AkkaHttpFacebookConnector(
    config.getString("wishkeeper.facebook.app-id"),
    config.getString("wishkeeper.facebook.app-secret"))
  private val userFriendsProjection: UserFriendsProjection = new DelegatingUserFriendsProjection(facebookConnector, userIdByFacebookIdProjection)
  private val imageStore = new GoogleCloudStorageImageStore
  private val webApi = new WebApi(this, this)
  private val imageProcessor: ImageProcessor = new ScrimageImageProcessor

  def start(): Unit = {
    dataStore.connect()
    webApi.start()
  }

  def stop(): Unit = {
    webApi.stop()
    dataStore.close()
  }

  override def connectFacebookUser(command: ConnectFacebookUser): Future[Boolean] = {
    val eventualIsValid = facebookConnector.isValid(command.authToken)
    eventualIsValid.map { isValid =>
      if (isValid) commandProcessor.process(command)
      isValid
    }
  }

  override def processCommand(command: UserCommand, sessionId: Option[UUID]): Unit = commandProcessor.process(command, sessionId)

  override def userProfileFor(sessionId: UUID): Option[UserProfile] = dataStore.userBySession(sessionId).map(profileFor)

  override def potentialFriendsFor(facebookAccessToken: String, sessionId: UUID): Option[Future[List[PotentialFriend]]] = {
    for {
      userId <- dataStore.userBySession(sessionId)
      socialData <- userProfileProjection.get(userId).socialData
      facebookId <- socialData.facebookId
    } yield userFriendsProjection.potentialFacebookFriends(facebookId, facebookAccessToken)
  }

  override def incomingFriendRequestSenders(sessionId: UUID): Option[List[UUID]] = {
    dataStore.userBySession(sessionId).map(incomingFriendRequestsProjection.awaitingApproval)
  }

  private val jpegContentType = "image/jpeg"

  override def uploadImage(inputStream: InputStream, imageMetadata: ImageMetadata, wishId: UUID, sessionId: UUID): Try[Unit] = {
    Try {
      val tempDir = Files.createDirectories(Paths.get("/tmp/wishkeeper-resize"))
      val origFile = Paths.get(tempDir.toString, imageMetadata.fileName)
      Files.copy(inputStream, origFile)

      List(
        imageProcessor.compress(origFile, ".full"),
        imageProcessor.resizeToWidth(origFile, ".fhd", 1080),
        imageProcessor.resizeToWidth(origFile, ".hfhd", 540)
      ).foreach { file =>
        imageStore.save(ImageData(Files.newInputStream(file), jpegContentType), file.getFileName.toString)
        Files.deleteIfExists(file)
      }
      Files.deleteIfExists(origFile)

      val url = s"$mediaServerBase/${imageMetadata.fileName}"
      processCommand(
        SetWishDetails(Wish(wishId, image = Option(ImageLink(url, imageMetadata.width, imageMetadata.height, imageMetadata.contentType)))),
        Option(sessionId)
      )
    }
  }

  override def userIdFor(facebookId: String): Option[UUID] = userIdByFacebookIdProjection.get(facebookId)

  override def profileFor(userId: UUID): UserProfile = userProfileProjection.get(userId)

  override def wishesFor(userId: UUID): List[Wish] = User.replay(dataStore.userEventsFor(userId)).wishes.values.toList

  override def wishListFor(userId: UUID): Option[UserWishes] = {
    dataStore.userBySession(userId).map { userId =>
      val wishList = User.replay(dataStore.userEventsFor(userId)).wishes.values.toList
      UserWishes(wishList.sortBy(_.creationTime.getMillis).reverse)
    }
  }
}

object Server {
  val mediaServerBase = "http://wish.media.wishkeeper.co"

  def main(args: Array[String] = Array.empty): Unit = {
    new WishkeeperServer().start()
  }
}