package co.wishkeeper.server.projections

import java.util.UUID

import co.wishkeeper.server.{DataStore, User, UserProfile}

trait UserProfileProjection {
  def get(userId: UUID): UserProfile
}

class ReplayingUserProfileProjection(dataStore: DataStore) extends UserProfileProjection {
  def get(userId: UUID): UserProfile = {
    val events = dataStore.userEventsFor(userId)
    User.replay(events).userProfile
  }
}
