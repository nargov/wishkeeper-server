package co.wishkeeper.server.projections

import java.util.UUID

import co.wishkeeper.server.{DataStore, User, UserProfile}

trait UserProfileProjection {
  def get(userId: UUID): UserProfile
}

class ReplayingUserProfileProjection(dataStore: DataStore) extends UserProfileProjection {
  def get(userId: UUID): UserProfile = User.replay(dataStore.userEvents(userId)).userProfile
}
