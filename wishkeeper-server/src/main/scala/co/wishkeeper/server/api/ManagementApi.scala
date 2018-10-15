package co.wishkeeper.server.api

import java.util.UUID

import co.wishkeeper.server.Events.UserEvent
import co.wishkeeper.server._
import co.wishkeeper.server.notifications.DeviceIdEventProcessor
import co.wishkeeper.server.projections.{Projection, UserIdByFacebookIdProjection, UserProfileProjection}
import co.wishkeeper.server.search.UserSearchProjection
import co.wishkeeper.server.user.commands.{DeleteUserPicture, SetFlagFacebookFriendsListSeen}

trait ManagementApi {
  def rebuildHistoryProjection(): Unit

  def resubscribePeriodicWakeup(): Unit

  def rebuildUserSearch(): Unit

  def userIdFor(facebookId: String): Option[UUID]

  def profileFor(userId: UUID): UserProfile

  def wishesFor(userId: UUID): List[Wish]

  def resetFacebookFriendsSeenFlag(userId: UUID): Unit

  def userByEmail(email: String): Option[UUID]

  def deleteUserPicture(userId: UUID): Either[Error, Unit]

  def userEvents(userId: UUID): List[UserEventInstant[_ <: UserEvent]]
}

class DelegatingManagementApi(userIdByFacebookIdProjection: UserIdByFacebookIdProjection,
                              userProfileProjection: UserProfileProjection,
                              dataStore: DataStore,
                              commandProcessor: CommandProcessor,
                              userSearchProjection: Projection,
                              deviceIdEventProcessor: DeviceIdEventProcessor,
                              historyProjection: Projection) extends ManagementApi {


  override def userByEmail(email: String): Option[UUID] = dataStore.userIdByEmail(email)

  override def resetFacebookFriendsSeenFlag(userId: UUID): Unit = commandProcessor.process(SetFlagFacebookFriendsListSeen(false), userId)

  override def userIdFor(facebookId: String): Option[UUID] = dataStore.userIdByFacebookId(facebookId)

  override def profileFor(userId: UUID): UserProfile = userProfileProjection.get(userId)

  override def wishesFor(userId: UUID): List[Wish] = User.replay(dataStore.userEvents(userId)).wishes.values.toList

  override def userEvents(userId: UUID): List[UserEventInstant[_ <: UserEvent]] = dataStore.userEvents(userId)

  override def deleteUserPicture(userId: UUID): Either[Error, Unit] = commandProcessor.validatedProcess(DeleteUserPicture, userId)

  override def rebuildUserSearch(): Unit = userSearchProjection.rebuild()

  override def resubscribePeriodicWakeup(): Unit = deviceIdEventProcessor.resubscribeAll()

  override def rebuildHistoryProjection(): Unit = historyProjection.rebuild()
}