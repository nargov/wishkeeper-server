package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.json._
import co.wishkeeper.server.Commands.SetFacebookUserInfo
import co.wishkeeper.server.Events._
import co.wishkeeper.server.WishkeeperServer.getValidUserBirthdayEvent
import org.joda.time.DateTime


trait UserInfoProvider {
  def userInfoForFacebookId(facebookId: String): Option[UserInfo]

  def saveFacebookUserInfo(info: SetFacebookUserInfo, userId: UUID): Unit
}

class WishkeeperServer() extends UserInfoProvider {
  private val dataStore = new CassandraDataStore
  private val sessionManager = new UserCommandProcessor(dataStore)
  private val webApi = new WebApi(dataStore, sessionManager, this)

  override def userInfoForFacebookId(facebookId: String): Option[UserInfo] = {
    dataStore.userInfoByFacebookId(facebookId).map(_.userInfo)
  }

  override def saveFacebookUserInfo(info: SetFacebookUserInfo, userId: UUID): Unit = {
    val events: Seq[UserEvent] = (info.age_range.map(range => UserAgeRangeSet(range.min, range.max)) ::
      info.birthday.flatMap(getValidUserBirthdayEvent) ::
      info.email.map(UserEmailSet) ::
      info.gender.map(UserGenderSet) ::
      info.locale.map(UserLocaleSet) ::
      info.timezone.map(UserTimeZoneSet) ::
      info.first_name.map(UserFirstNameSet) ::
      info.last_name.map(UserLastNameSet) ::
      info.name.map(UserNameSet) :: Nil).flatten

    dataStore.saveUserEvents(userId, dataStore.lastSequenceNum(userId), DateTime.now(), events)
  }



  def start(): Unit = {
    webApi.start()
  }
}

object WishkeeperServer {
  val getValidUserBirthdayEvent: String => Option[UserBirthdaySet] = day => {
    if (day.matches("""^\d{2}/\d{2}(/\d{4})?$"""))
      Some(UserBirthdaySet(day))
    else None
  }
}

object Server {

  def main(args: Array[String] = Array.empty): Unit = {
    new WishkeeperServer().start()
  }
}

case class UserInfoInstance(userInfo: UserInfo, seq: Long)

case class UserSession(userId: UUID, sessionId: UUID)