package co.wishkeeper.server

import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import co.wishkeeper.json._
import co.wishkeeper.server.api.{DelegatingManagementApi, DelegatingPublicApi, ManagementApi}
import co.wishkeeper.server.events.processing.ImageUploadEventProcessor
import co.wishkeeper.server.image.{CloudinaryConfig, CloudinaryImageStore, GoogleCloudStorageImageStore, ImageStore}
import co.wishkeeper.server.messaging._
import co.wishkeeper.server.notifications.{DeviceIdEventProcessor, ExecutorNotificationsScheduler, ReportingEventProcessor, ServerNotificationEventProcessor}
import co.wishkeeper.server.projections._
import co.wishkeeper.server.reporting.{DebugReporter, Reporter, SlackBotReporter}
import co.wishkeeper.server.search.SimpleScanUserSearchProjection
import co.wishkeeper.server.web.WebApi
import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, ExecutionContextExecutorService}


class WishkeeperServer {
  private val config = ConfigFactory.load()

  private implicit val system: ActorSystem = ActorSystem("web-api")
  private implicit val materializer: ActorMaterializer = ActorMaterializer()
  private implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  private val dataStoreConfig = DataStoreConfig(config.getStringList("wishkeeper.datastore.urls").asScala.toList)
  private val dataStore: DataStore = new CassandraDataStore(dataStoreConfig)

  private val clientRegistry = new MemStateClientRegistry()(ExecutionContext.fromExecutor(Executors.newFixedThreadPool(5)))

  private val fileAdapter = new JavaFileAdapter
  private val wishImageStore: ImageStore = new GoogleCloudStorageImageStore(config.getString("wishkeeper.image-store.bucket-name"))
  private val userImageStore: ImageStore = new GoogleCloudStorageImageStore(config.getString("wishkeeper.user-image-store.bucket-name"))

  private val userIdByFacebookIdProjection = new DataStoreUserIdByFacebookIdProjection(dataStore)
  private val pushNotifications = new FirebasePushNotificationSender(config = FirebaseConfig(config.getString("wishkeeper.fcm.key")))
  private val notificationsScheduler = new ExecutorNotificationsScheduler(clientNotifier = clientRegistry, dataStore = dataStore,
    pushNotifications = pushNotifications)
  private val notificationsProjection = new DataStoreNotificationsProjection(dataStore)
  private val googleAuth = new SdkGoogleAuthAdapter
  private val firebaseAuth = new FirebaseEmailAuthProvider

  private val facebookConnector: FacebookConnector = new AkkaHttpFacebookConnector(
    config.getString("wishkeeper.facebook.app-id"),
    config.getString("wishkeeper.facebook.app-secret"))

  private val friendRequestsProjection = new FriendRequestsEventProcessor(dataStore)
  private val userFriendsProjection = new EventBasedUserFriendsProjection(facebookConnector, googleAuth, userIdByFacebookIdProjection, dataStore)
  private val userSearchProjection = new SimpleScanUserSearchProjection(dataStore)
  private val userHistoryProjection = new ScanningUserHistoryProjection(dataStore, clientRegistry)
  private val deviceIdEventProcessor = new DeviceIdEventProcessor(pushNotifications, dataStore)

  private val slackUrlConfigKey = "wishkeeper.slack.url"
  private val reporter: Reporter = {
    if(config.hasPath(slackUrlConfigKey))
      new SlackBotReporter(config.getString(slackUrlConfigKey))
    else
      DebugReporter
  }

  private val commandProcessor: CommandProcessor = new UserCommandProcessor(dataStore, List(
    userIdByFacebookIdProjection,
    notificationsProjection,
    friendRequestsProjection,
    new UserByEmailProjection(dataStore),
    new ServerNotificationEventProcessor(clientRegistry, notificationsScheduler, dataStore, pushNotifications),
    new ImageUploadEventProcessor(userImageStore, fileAdapter, dataStore),
    userSearchProjection,
    new ReportingEventProcessor(reporter, dataStore),
    deviceIdEventProcessor,
    userHistoryProjection
  ), googleAuth, firebaseAuth)
  private val userProfileProjection: UserProfileProjection = new ReplayingUserProfileProjection(dataStore)
  private val mailgunApiKey: String = config.getString("wishkeeper.mail.mailgun.api.key")
  private val emailSender = new EmailSender(new MailgunEmailProvider(mailgunApiKey), new ScalateTemplateEngine)

  private val cloudinaryConfig = CloudinaryConfig(
    config.getString("wishkeeper.cloudinary.cloud-name"),
    config.getString("wishkeeper.cloudinary.api-key"),
    config.getString("wishkeeper.cloudinary.api-secret"))
  private val cloudinaryExecutionContext = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(5))
  private val cloudinaryImageStore = new CloudinaryImageStore(cloudinaryConfig)(cloudinaryExecutionContext)

  private val publicApi = new DelegatingPublicApi(commandProcessor, dataStore, facebookConnector, userProfileProjection, userFriendsProjection,
    notificationsProjection, userSearchProjection, wishImageStore, userImageStore, userHistoryProjection, googleAuth, firebaseAuth, emailSender,
    cloudinaryImageStore)
  private val managementApi: ManagementApi = new DelegatingManagementApi(userIdByFacebookIdProjection, userProfileProjection,
    dataStore, commandProcessor, userSearchProjection, deviceIdEventProcessor, userHistoryProjection)
  private val webApi = new WebApi(publicApi, managementApi, clientRegistry, new StaticFileFeatureToggles)

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