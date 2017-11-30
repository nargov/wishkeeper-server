package co.wishkeeper.server.api

import java.util.UUID

import co.wishkeeper.server.Commands.SetFlagFacebookFriendsListSeen
import co.wishkeeper.server._
import co.wishkeeper.server.projections.{UserIdByFacebookIdProjection, UserProfileProjection}

trait ManagementApi {
  def userIdFor(facebookId: String): Option[UUID]

  def profileFor(userId: UUID): UserProfile

  def wishesFor(userId: UUID): List[Wish]

  def resetFacebookFriendsSeenFlag(userId: UUID): Unit

  def userByEmail(email: String): Option[UUID]
}

class DelegatingManagementApi(userIdByFacebookIdProjection: UserIdByFacebookIdProjection,
                              userProfileProjection: UserProfileProjection,
                              dataStore: DataStore,
                              commandProcessor: CommandProcessor) extends ManagementApi {

  override def userByEmail(email: String): Option[UUID] = dataStore.userByEmail(email)

  override def resetFacebookFriendsSeenFlag(userId: UUID): Unit = commandProcessor.process(SetFlagFacebookFriendsListSeen(false), userId)

  override def userIdFor(facebookId: String): Option[UUID] = dataStore.userIdByFacebookId(facebookId)

  override def profileFor(userId: UUID): UserProfile = userProfileProjection.get(userId)

  override def wishesFor(userId: UUID): List[Wish] = User.replay(dataStore.userEvents(userId)).wishes.values.toList

}