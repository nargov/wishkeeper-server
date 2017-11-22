package co.wishkeeper.server.projections

import java.util.UUID

import co.wishkeeper.server.{DataStore, User, UserProfile}

trait UserProfileProjection {
  def get(userId: UUID): UserProfile

  private val leaveOnlyStrangerAllowedFields: UserProfile => UserProfile = profile =>
    UserProfile(name = profile.name, picture = profile.picture, firstName = profile.firstName, gender = profile.gender)

  val strangerProfile: UUID => UserProfile = get _ andThen leaveOnlyStrangerAllowedFields
}

class ReplayingUserProfileProjection(dataStore: DataStore) extends UserProfileProjection {
  def get(userId: UUID): UserProfile = User.replay(dataStore.userEvents(userId)).userProfile
}
