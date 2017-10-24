package co.wishkeeper.server.projections

import co.wishkeeper.server.CommandProcessor.retry
import co.wishkeeper.server.Events.{Event, FriendRequestStatusChanged}
import co.wishkeeper.server.{DataStore, EventProcessor}
import org.joda.time.DateTime


trait FriendRequestsProjection

class DataStoreFriendRequestsProjection(dataStore: DataStore) extends FriendRequestsProjection with EventProcessor {
  override def process(event: Event): Unit = {
    event match {
      case FriendRequestStatusChanged(_, requestId, from, status) =>
        retry{
          dataStore.saveUserEvents(from, dataStore.lastSequenceNum(from), DateTime.now(), List(
            FriendRequestStatusChanged(from, requestId, from, status)
          ))
        }
      case _ =>
    }
  }
}
