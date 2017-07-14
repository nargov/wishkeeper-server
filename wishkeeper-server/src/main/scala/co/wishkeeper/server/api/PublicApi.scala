package co.wishkeeper.server.api

import java.io.InputStream
import java.util.UUID

import co.wishkeeper.server.Commands.{ConnectFacebookUser, UserCommand}
import co.wishkeeper.server.image.ImageMetadata
import co.wishkeeper.server.projections.PotentialFriend
import co.wishkeeper.server.{UserProfile, UserWishes}

import scala.concurrent.Future
import scala.util.Try

trait PublicApi {
  def deleteWish(sessionId: UUID, wishId: UUID): Unit

  def wishListFor(sessionId: UUID): Option[UserWishes]

  def processCommand(command: UserCommand, sessionId: Option[UUID]): Unit

  def connectFacebookUser(command: ConnectFacebookUser): Future[Boolean]

  def userProfileFor(sessionId: UUID): Option[UserProfile]

  def potentialFriendsFor(facebookAccessToken: String, sessionId: UUID): Option[Future[List[PotentialFriend]]]

  def incomingFriendRequestSenders(sessionId: UUID): Option[List[UUID]]

  def uploadImage(inputStream: InputStream, imageMetadata: ImageMetadata, wishId: UUID, sessionId: UUID): Try[Unit]

  def uploadImage(url: String, imageMetadata: ImageMetadata, wishId: UUID, sessionId: UUID): Try[Unit]

  def deleteWishImage(sessionId: UUID, wishId: UUID): Unit
}
