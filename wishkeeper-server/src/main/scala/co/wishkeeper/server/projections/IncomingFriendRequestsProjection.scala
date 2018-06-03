package co.wishkeeper.server.projections

import java.util.UUID

import co.wishkeeper.server.Events._
import co.wishkeeper.server._
import co.wishkeeper.server.messaging.{NotificationsUpdated, ServerNotification}
import org.joda.time.DateTime


// TODO change this to be an actual projection, and not save new events.
class IncomingFriendRequestsProjection(dataStore: DataStore, notifyClient: (ServerNotification, UUID) => Unit) extends EventProcessor {

  override def process(event: Event, userId: UUID): Unit = event match {
    case FriendRequestSent(sender, targetUserId, id) =>
      id.foreach { _ =>
        val lastSequenceNum = dataStore.lastSequenceNum(targetUserId)
        val result = CommandProcessor.retry {
          Either.cond(dataStore.saveUserEvents(targetUserId, lastSequenceNum, DateTime.now(), List(
            FriendRequestReceived(targetUserId, sender, id)
          )), (), DbErrorEventsNotSaved)
        }
        result.foreach(_ => {
          notifyClient(NotificationsUpdated, targetUserId)
        })
      }
    case _ =>
  }
}

