package co.wishkeeper.server

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import co.wishkeeper.json._
import co.wishkeeper.server.projections._

import scala.concurrent.ExecutionContextExecutor


class WishkeeperServer() {
  private val dataStore = new CassandraDataStore
  private val userIdByFacebookIdProjection = new DataStoreUserIdByFacebookIdProjection(dataStore)
  private val commandProcessor: CommandProcessor = new UserCommandProcessor(dataStore, userIdByFacebookIdProjection :: Nil)
  private val userProfileProjection: UserProfileProjection = new ReplayingUserProfileProjection(dataStore)

  private implicit val system = ActorSystem("web-api")
  private implicit val materializer = ActorMaterializer()
  private implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  private val facebookConnector: FacebookConnector = new AkkaHttpFacebookConnector
  private val userFriendsProjection: UserFriendsProjection = new DelegatingUserFriendsProjection(facebookConnector, userIdByFacebookIdProjection)
  private val webApi = new WebApi(commandProcessor, userIdByFacebookIdProjection, userProfileProjection, dataStore, userFriendsProjection)

  def start(): Unit = {
    webApi.start()
  }
}

object Server {

  def main(args: Array[String] = Array.empty): Unit = {
    new WishkeeperServer().start()
  }
}