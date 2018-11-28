package co.wishkeeper.server.projections

import java.util.UUID

import co.wishkeeper.server.Events.{UserEvent, UserFacebookIdSet}
import co.wishkeeper.server.{DataStore, EventProcessor, UserEventInstance}

trait UserIdByFacebookIdProjection {
  def get(facebookIds: List[String]): Map[String, UUID]
}

class DataStoreUserIdByFacebookIdProjection(dataStore: DataStore) extends UserIdByFacebookIdProjection with EventProcessor {

  override def get(facebookIds: List[String]): Map[String, UUID] = dataStore.userIdsByFacebookIds(facebookIds)

  override def process[E <: UserEvent](instance: UserEventInstance[E]): List[UserEventInstance[_ <: UserEvent]] = {
    instance.event match {
      case UserFacebookIdSet(_, facebookId) => dataStore.saveUserIdByFacebookId(facebookId, instance.userId)
      case _ =>
    }
    Nil
  }
}

