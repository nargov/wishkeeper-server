package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.Commands.ConnectFacebookUser
import org.joda.time.DateTime

import scala.util.Try

trait CommandProcessor {
  def processConnectFacebookUser(connectUser: ConnectFacebookUser): UserSession

  def userIdForSession(sessionId: UUID): Option[UUID]

  def decodeSession(encoded: String): Option[UUID]
}

class UserCommandProcessor(dataStore: DataStore) extends CommandProcessor { //TODO Need to break this up
  override def processConnectFacebookUser(connectUser: ConnectFacebookUser): UserSession = {
    val user = User.createNew()
    val sessionId = UUID.randomUUID()
    val events = user.processCommand(connectUser)
    val lastSeqNum = dataStore.lastSequenceNum(user.id)
    val now = DateTime.now()
    dataStore.saveUserEvents(user.id, lastSeqNum, now, events)
    val userInfo = UserInfo(user.id, Option(FacebookData(connectUser.facebookId)))
    dataStore.updateFacebookIdToUserInfo(connectUser.facebookId, None, userInfo) //TODO get last seq number from table
    dataStore.saveUserSession(user.id, sessionId, now)
    UserSession(user.id, sessionId)
  }

  //TODO Move this somewhere else - particular view, probably
  override def userIdForSession(sessionId: UUID): Option[UUID] = dataStore.userBySession(sessionId)

  override def decodeSession(encoded: String): Option[UUID] = Try(UUID.fromString(encoded)).toOption
}

