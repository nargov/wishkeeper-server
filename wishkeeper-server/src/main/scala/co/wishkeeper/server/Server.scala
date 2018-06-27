package co.wishkeeper.server

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import co.wishkeeper.json._
import co.wishkeeper.server.api.{DelegatingManagementApi, DelegatingPublicApi, ManagementApi}
import co.wishkeeper.server.image.{GoogleCloudStorageImageStore, ImageStore}
import co.wishkeeper.server.messaging.MemStateClientRegistry
import co.wishkeeper.server.notifications.{ExecutorNotificationsScheduler, ServerNotificationEventProcessor}
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

  private val clientRegistry = new MemStateClientRegistry

  private val userIdByFacebookIdProjection = new DataStoreUserIdByFacebookIdProjection(dataStore)
  private val notificationsScheduler = new ExecutorNotificationsScheduler(clientNotifier = clientRegistry, dataStore = dataStore)
  private val notificationsProjection = new DataStoreNotificationsProjection(dataStore)

  private val facebookConnector: FacebookConnector = new AkkaHttpFacebookConnector(
    config.getString("wishkeeper.facebook.app-id"),
    config.getString("wishkeeper.facebook.app-secret"))

  private val friendRequestsProjection = new FriendRequestsEventProcessor(dataStore)
  private val userFriendsProjection = new EventBasedUserFriendsProjection(facebookConnector, userIdByFacebookIdProjection, dataStore)

  private val commandProcessor: CommandProcessor = new UserCommandProcessor(dataStore, List(
    userIdByFacebookIdProjection,
    notificationsProjection,
    friendRequestsProjection,
    new UserByEmailProjection(dataStore),
    new ServerNotificationEventProcessor(clientRegistry, notificationsScheduler)
  ))
  private val userProfileProjection: UserProfileProjection = new ReplayingUserProfileProjection(dataStore)
  private val imageStore: ImageStore = new GoogleCloudStorageImageStore(config.getString("wishkeeper.image-store.bucket-name"))
  private val publicApi = new DelegatingPublicApi(commandProcessor, dataStore, facebookConnector,
    userProfileProjection, userFriendsProjection, notificationsProjection, imageStore)
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