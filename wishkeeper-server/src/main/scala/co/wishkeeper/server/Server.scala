package co.wishkeeper.server

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import co.wishkeeper.json._
import co.wishkeeper.server.api.{DelegatingManagementApi, DelegatingPublicApi, ManagementApi}
import co.wishkeeper.server.image.{GoogleCloudStorageImageStore, ImageStore}
import co.wishkeeper.server.projections._
import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContextExecutor


class WishkeeperServer{
  private val config = ConfigFactory.load()
  private val dataStoreConfig = DataStoreConfig(config.getStringList("wishkeeper.datastore.urls").asScala.toList)
  private val dataStore = new CassandraDataStore(dataStoreConfig)
  private val userIdByFacebookIdProjection = new DataStoreUserIdByFacebookIdProjection(dataStore)
  private val incomingFriendRequestsProjection = new DataStoreIncomingFriendRequestsProjection(dataStore)
  private val commandProcessor: CommandProcessor = new UserCommandProcessor(dataStore,
    List(userIdByFacebookIdProjection, incomingFriendRequestsProjection))
  private val userProfileProjection: UserProfileProjection = new ReplayingUserProfileProjection(dataStore)

  private implicit val system = ActorSystem("web-api")
  private implicit val materializer = ActorMaterializer()
  private implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  private val facebookConnector: FacebookConnector = new AkkaHttpFacebookConnector(
    config.getString("wishkeeper.facebook.app-id"),
    config.getString("wishkeeper.facebook.app-secret"))
  private val userFriendsProjection: UserFriendsProjection = new DelegatingUserFriendsProjection(facebookConnector, userIdByFacebookIdProjection)
  private val imageStore: ImageStore = new GoogleCloudStorageImageStore(config.getString("wishkeeper.image-store.bucket-name"))
  private val publicApi = new DelegatingPublicApi(commandProcessor, dataStore, facebookConnector,
    incomingFriendRequestsProjection, userProfileProjection, userFriendsProjection, imageStore)
  private val managementApi: ManagementApi = new DelegatingManagementApi(userIdByFacebookIdProjection, userProfileProjection,
    dataStore, commandProcessor)
  private val webApi = new WebApi(publicApi, managementApi)

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