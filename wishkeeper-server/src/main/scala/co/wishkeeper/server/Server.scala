package co.wishkeeper.server

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import co.wishkeeper.json._
import co.wishkeeper.server.api.{DelegatingManagementApi, DelegatingPublicApi, ManagementApi}
import co.wishkeeper.server.image.{GoogleCloudStorageImageStore, ImageStore}
import co.wishkeeper.server.messaging.ClientRegistry
import co.wishkeeper.server.projections._
import co.wishkeeper.server.web.WebApi
import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContextExecutor


class WishkeeperServer {
  private val config = ConfigFactory.load()

  private implicit val system = ActorSystem("web-api")
  private implicit val materializer = ActorMaterializer()
  private implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  private val dataStoreConfig = DataStoreConfig(config.getStringList("wishkeeper.datastore.urls").asScala.toList)
  private val dataStore: DataStore = new CassandraDataStore(dataStoreConfig)

  private val clientRegistry = new ClientRegistry

  private val userIdByFacebookIdProjection = new DataStoreUserIdByFacebookIdProjection(dataStore)
  private val incomingFriendRequestsProjection = new DataStoreIncomingFriendRequestsProjection(dataStore, clientRegistry.sendTo)
  private val notificationsProjection: NotificationsProjection = new DataStoreNotificationsProjection(dataStore)

  private val facebookConnector: FacebookConnector = new AkkaHttpFacebookConnector(
    config.getString("wishkeeper.facebook.app-id"),
    config.getString("wishkeeper.facebook.app-secret"))

  // TODO the following projection is probably not necessary.
  // The only reason it exists now is so that events are created for a different user.
  // Rethink the way command creates events - can a command create events for multiple aggregate roots (users)?
  private val friendRequestsProjection = new DataStoreFriendRequestsProjection(dataStore)
  private val userFriendsProjection = new EventBasedUserFriendsProjection(facebookConnector, userIdByFacebookIdProjection, dataStore)

  private val commandProcessor: CommandProcessor = new UserCommandProcessor(dataStore, List(
    userIdByFacebookIdProjection,
    incomingFriendRequestsProjection,
    notificationsProjection,
    friendRequestsProjection,
    new UserByEmailProjection(dataStore)
  ))
  private val userProfileProjection: UserProfileProjection = new ReplayingUserProfileProjection(dataStore)
  private val imageStore: ImageStore = new GoogleCloudStorageImageStore(config.getString("wishkeeper.image-store.bucket-name"))
  private val publicApi = new DelegatingPublicApi(commandProcessor, dataStore, facebookConnector,
    incomingFriendRequestsProjection, userProfileProjection, userFriendsProjection, notificationsProjection, imageStore)
  private val managementApi: ManagementApi = new DelegatingManagementApi(userIdByFacebookIdProjection, userProfileProjection,
    dataStore, commandProcessor)
  private val webApi = new WebApi(publicApi, managementApi, clientRegistry)

  def start(): Unit = {
    dataStore.connect()
    webApi.start()
    println("Wishkeeper Server started")
  }

  def stop(): Unit = {
    webApi.stop()
    dataStore.close()
  }

}

object Server {
  def main(args: Array[String] = Array.empty): Unit = {
    new WishkeeperServer().start()
  }
}