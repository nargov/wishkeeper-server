package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.json._
import co.wishkeeper.server.Commands.{ConnectFacebookUser, SetFacebookUserInfo}
import co.wishkeeper.server.Events._
import co.wishkeeper.server.WishkeeperServer.getValidUserBirthdayEvent
import org.joda.time.DateTime

import scala.util.Try

trait SessionManager {
  def saveUserSessionConnected(connectUser: ConnectFacebookUser): UserSession

  def userIdForSession(sessionId: UUID): Option[UUID]

  def decodeSession(encoded: String): Option[UUID]

  def saveFacebookUserInfo(info: SetFacebookUserInfo, userId: UUID): Unit
}

class WishkeeperServer() extends SessionManager {
  private val eventStore = new CassandraEventStore
  private val webApi = new WebApi(eventStore, this)

  def start(): Unit = {
    webApi.start()
  }

  override def saveUserSessionConnected(connectUser: ConnectFacebookUser): UserSession = {
    val userId = UUID.randomUUID()
    val sessionId = UUID.randomUUID()
    val now = DateTime.now()
    val userConnectedEvent = UserConnected(userId, now, sessionId)
    val facebookIdSetEvent = UserFacebookIdSet(userId, connectUser.facebookId)
    val events = List(userConnectedEvent, facebookIdSetEvent)
    val lastSeqNum = eventStore.lastSequenceNum(userId)
    eventStore.saveUserEvents(userId, lastSeqNum, now, events)
    val userInfo = UserInfo(userId, Option(FacebookData(connectUser.facebookId)))
    eventStore.updateFacebookIdToUserInfo(connectUser.facebookId, None, userInfo) //TODO get last seq number from table
    eventStore.saveUserSession(userId, sessionId, now)
    UserSession(userId, sessionId)
  }

  def saveFacebookUserInfo(info: SetFacebookUserInfo, userId: UUID): Unit = {
    val events: Seq[UserEvent] = (info.age_range.map(range => UserAgeRangeSet(range.min, range.max)) ::
      info.birthday.flatMap(getValidUserBirthdayEvent) ::
      info.email.map(UserEmailSet) ::
      info.gender.map(UserGenderSet) ::
      info.locale.map(UserLocaleSet) ::
      info.timezone.map(UserTimeZoneSet) ::
      info.first_name.map(UserFirstNameSet) ::
      info.last_name.map(UserLastNameSet) ::
      info.name.map(UserNameSet) :: Nil).flatten

    eventStore.saveUserEvents(userId, eventStore.lastSequenceNum(userId), DateTime.now(), events)
  }

  override def userIdForSession(sessionId: UUID): Option[UUID] = eventStore.userBySession(sessionId)

  override def decodeSession(encoded: String): Option[UUID] = Try(UUID.fromString(encoded)).toOption
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