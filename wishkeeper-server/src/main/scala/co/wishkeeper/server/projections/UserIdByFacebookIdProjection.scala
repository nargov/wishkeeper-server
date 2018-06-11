package co.wishkeeper.server.projections

import java.util.UUID

import co.wishkeeper.server.Events.{Event, UserFacebookIdSet}
import co.wishkeeper.server.{DataStore, EventProcessor}

trait UserIdByFacebookIdProjection {
  def get(facebookIds: List[String]): Map[String, UUID]
}

class DataStoreUserIdByFacebookIdProjection(dataStore: DataStore) extends UserIdByFacebookIdProjection with EventProcessor {

  override def get(facebookIds: List[String]): Map[String, UUID] = dataStore.userIdsByFacebookIds(facebookIds)

  override def process(event: Event, userId: UUID): List[(UUID, Event)] = {
    event match {
      case UserFacebookIdSet(_, facebookId) => dataStore.saveUserIdByFacebookId(facebookId, userId)
      case _ =>
    }
    Nil
  }
}

