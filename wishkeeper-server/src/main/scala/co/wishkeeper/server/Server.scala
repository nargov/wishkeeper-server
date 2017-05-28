package co.wishkeeper.server

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import co.wishkeeper.json._
import co.wishkeeper.server.projections._
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContextExecutor


class WishkeeperServer() {
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
  private val webApi = new WebApi(commandProcessor, userIdByFacebookIdProjection, userProfileProjection, dataStore, userFriendsProjection,
    facebookConnector, incomingFriendRequestsProjection, imageStore)

  def start(): Unit = {
    dataStore.connect()
    webApi.start()
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

trait WishkeeperPublicApi {

}