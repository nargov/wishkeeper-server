package co.wishkeeper.server

import co.wishkeeper.json._


class WishkeeperServer() {
  private val dataStore = new CassandraDataStore
  private val userIdByFacebookIdProjection = new UserIdByFacebookIdProjection(dataStore)
  private val commandProcessor: CommandProcessor = new UserCommandProcessor(dataStore, userIdByFacebookIdProjection :: Nil)
  private val userProfileProjection: UserProfileProjection = new UserEventsUserProfileProjection(dataStore)
  private val webApi = new WebApi(dataStore, commandProcessor, userIdByFacebookIdProjection, userProfileProjection)

  def start(): Unit = {
    webApi.start()
  }
}

object Server {

  def main(args: Array[String] = Array.empty): Unit = {
    new WishkeeperServer().start()
  }
}