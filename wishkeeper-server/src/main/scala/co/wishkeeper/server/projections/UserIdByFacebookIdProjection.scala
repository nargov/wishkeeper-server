package co.wishkeeper.server.projections

import java.util.UUID

import co.wishkeeper.server.Events.{Event, UserFacebookIdSet}
import co.wishkeeper.server.{DataStore, EventProcessor, Events}

trait UserIdByFacebookIdProjection {

  def get(facebookId: String): Option[UUID]

  def get(facebookIds: List[String]): Map[String, UUID]

  def process(event: Events.Event): Unit
}

class DataStoreUserIdByFacebookIdProjection(dataStore: DataStore) extends UserIdByFacebookIdProjection with EventProcessor {

  override def get(facebookId: String): Option[UUID] = dataStore.userIdByFacebookId(facebookId)

  override def get(facebookIds: List[String]): Map[String, UUID] = dataStore.userIdsByFacebookIds(facebookIds)

  override def process(event: Event): Unit = event match {
    case UserFacebookIdSet(userId, facebookId) => dataStore.saveUserIdByFacebookId(facebookId, userId)
    case _ => //ignore all other events
  }
}

