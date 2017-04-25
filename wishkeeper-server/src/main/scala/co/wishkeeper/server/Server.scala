package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.json._

class WishkeeperServer() {
  private val eventStore = new CassandraEventStore
  private val webApi = new WebApi(eventStore)

  def start(): Unit = {
    webApi.start()
  }

}

object Server {

  def main(args: Array[String] = Array.empty): Unit = {
    new WishkeeperServer().start()
  }
}



case class Wish(name: String, id: UUID = UUID.randomUUID())

case object GetUserWishes


case class UserInfoInstance(userInfo: UserInfo, seq: Long)

case class ConnectResponse(userId: UUID, sessionId: UUID) {
  //TODO add def fromUserConnected
}

case class SetFacebookUserInfo(age_range: Option[FacebookAgeRange],
                               birthday: Option[String],
                               email: Option[String],
                               first_name: Option[String],
                               last_name: Option[String],
                               name: Option[String],
                               gender: Option[String],
                               locale: Option[String],
                               timezone: Option[Int])

case class FacebookAgeRange(min: Option[Int], max: Option[Int])

case class UserFromSession(session: UUID)