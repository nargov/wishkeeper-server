package co.wishkeeper.server.api

import java.io.InputStream
import java.util.UUID

import co.wishkeeper.server.Commands.{ConnectFacebookUser, UserCommand}
import co.wishkeeper.server.{PotentialFriend, UserProfile}

import scala.concurrent.Future
import scala.util.Try

trait PublicApi {
  def processCommand(command: UserCommand, sessionId: Option[UUID]): Unit

  def connectFacebookUser(command: ConnectFacebookUser): Future[Boolean]

  def userProfileFor(sessionId: UUID): Option[UserProfile]

  def potentialFriendsFor(facebookAccessToken: String, sessionId: UUID): Option[Future[List[PotentialFriend]]]

  def incomingFriendRequestSenders(sessionId: UUID): Option[List[UUID]]

  def uploadImage(inputStream: InputStream, contentType: String, fileName: String, wishId: UUID, sessionId: UUID): Try[Unit]
}
