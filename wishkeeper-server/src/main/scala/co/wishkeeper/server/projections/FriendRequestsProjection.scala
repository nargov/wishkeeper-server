package co.wishkeeper.server.projections

import java.util.UUID

import co.wishkeeper.server.CommandProcessor.retry
import co.wishkeeper.server.Events.{Event, FriendRemoved, FriendRequestStatusChanged, UserEvent}
import co.wishkeeper.server.{DataStore, EventProcessor}
import org.joda.time.DateTime


trait FriendRequestsProjection

class DataStoreFriendRequestsProjection(dataStore: DataStore) extends FriendRequestsProjection with EventProcessor {
  override def process(event: Event): Unit = {
    event match {
      case FriendRequestStatusChanged(_, requestId, from, status) => saveEventFor(from, FriendRequestStatusChanged(from, requestId, from, status))
      case FriendRemoved(userId, friendId) => saveEventFor(friendId, FriendRemoved(friendId, userId))
      case _ =>
    }
  }

  private def saveEventFor(userId: UUID, event: UserEvent) = retry {
    dataStore.saveUserEvents(userId, dataStore.lastSequenceNum(userId), DateTime.now(), List(event))
  }
}
