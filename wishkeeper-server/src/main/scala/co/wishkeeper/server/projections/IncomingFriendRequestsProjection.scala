package co.wishkeeper.server.projections

import java.util.UUID

import co.wishkeeper.server.Events._
import co.wishkeeper.server._
import co.wishkeeper.server.messaging.{NotificationsUpdated, ServerNotification}
import org.joda.time.DateTime


trait IncomingFriendRequestsProjection


// TODO change this to be an actual projection, and not save new events.
class DataStoreIncomingFriendRequestsProjection(dataStore: DataStore, notifyClient: (String, UUID) => Unit)
  extends IncomingFriendRequestsProjection with EventProcessor {

  override def process(event: Event): Unit = event match {
    case FriendRequestSent(sender, userId, id) =>
      id.foreach { _ =>
        val lastSequenceNum = dataStore.lastSequenceNum(userId)
        val result = CommandProcessor.retry {
          Either.cond(dataStore.saveUserEvents(userId, lastSequenceNum, DateTime.now(), List(
            FriendRequestReceived(userId, sender, id)
          )), (), DbErrorEventsNotSaved)
        }
        result.foreach(_ => notifyClient(ServerNotification.toJson(NotificationsUpdated), userId))
      }
    case _ =>
  }
}

