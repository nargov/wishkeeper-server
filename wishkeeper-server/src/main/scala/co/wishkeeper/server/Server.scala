package co.wishkeeper.server

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import co.wishkeeper.json._
import co.wishkeeper.server.api.{DelegatingManagementApi, DelegatingPublicApi, ManagementApi}
import co.wishkeeper.server.events.processing.ImageUploadEventProcessor
import co.wishkeeper.server.image.{GoogleCloudStorageImageStore, ImageStore}
import co.wishkeeper.server.messaging.{FirebasePushNotifications, MemStateClientRegistry}
import co.wishkeeper.server.notifications.{ExecutorNotificationsScheduler, ServerNotificationEventProcessor}
import co.wishkeeper.server.projections._
import co.wishkeeper.server.search.SimpleScanUserSearchProjection
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

  private val fileAdapter = new JavaFileAdapter
  private val wishImageStore: ImageStore = new GoogleCloudStorageImageStore(config.getString("wishkeeper.image-store.bucket-name"))
  private val userImageStore: ImageStore = new GoogleCloudStorageImageStore(config.getString("wishkeeper.user-image-store.bucket-name"))

  private val userIdByFacebookIdProjection = new DataStoreUserIdByFacebookIdProjection(dataStore)
  private val pushNotifications = new FirebasePushNotifications
  private val notificationsScheduler = new ExecutorNotificationsScheduler(clientNotifier = clientRegistry, dataStore = dataStore,
    pushNotifications = pushNotifications)
  private val notificationsProjection = new DataStoreNotificationsProjection(dataStore)

  private val facebookConnector: FacebookConnector = new AkkaHttpFacebookConnector(
    config.getString("wishkeeper.facebook.app-id"),
    config.getString("wishkeeper.facebook.app-secret"))

  private val friendRequestsProjection = new FriendRequestsEventProcessor(dataStore)
  private val userFriendsProjection = new EventBasedUserFriendsProjection(facebookConnector, userIdByFacebookIdProjection, dataStore)
  private val userSearchProjection = new SimpleScanUserSearchProjection(dataStore)

  private val commandProcessor: CommandProcessor = new UserCommandProcessor(dataStore, List(
    userIdByFacebookIdProjection,
    notificationsProjection,
    friendRequestsProjection,
    new UserByEmailProjection(dataStore),
    new ServerNotificationEventProcessor(clientRegistry, notificationsScheduler, dataStore, pushNotifications),
    new ImageUploadEventProcessor(userImageStore, fileAdapter, dataStore),
    userSearchProjection
  ))
  private val userProfileProjection: UserProfileProjection = new ReplayingUserProfileProjection(dataStore)
  private val publicApi = new DelegatingPublicApi(commandProcessor, dataStore, facebookConnector,
    userProfileProjection, userFriendsProjection, notificationsProjection, userSearchProjection, wishImageStore)
  private val managementApi: ManagementApi = new DelegatingManagementApi(userIdByFacebookIdProjection, userProfileProjection,
    dataStore, commandProcessor, userSearchProjection)
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