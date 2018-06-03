package co.wishkeeper.server.projections

import java.util.UUID

import co.wishkeeper.server.CommandProcessor.retry
import co.wishkeeper.server.Events.{Event, FriendRemoved, FriendRequestStatusChanged, UserEvent}
import co.wishkeeper.server.{DataStore, DbErrorEventsNotSaved, EventProcessor}
import org.joda.time.DateTime


trait FriendRequestsProjection

class DataStoreFriendRequestsProjection(dataStore: DataStore) extends FriendRequestsProjection with EventProcessor {
  override def process(event: Event, userId: UUID): Unit = {
    event match {
      case FriendRequestStatusChanged(_, requestId, from, status) => saveEventFor(from, FriendRequestStatusChanged(from, requestId, from, status))
      case FriendRemoved(_, friendId) => saveEventFor(friendId, FriendRemoved(friendId, userId))
      case _ =>
    }
  }

  private def saveEventFor(userId: UUID, event: UserEvent) = retry {
    Either.cond(dataStore.saveUserEvents(userId, dataStore.lastSequenceNum(userId), DateTime.now(), List(event)), (), DbErrorEventsNotSaved)
  }
}
