package co.wishkeeper.server.projections

import co.wishkeeper.server.Events._
import co.wishkeeper.server._
import org.joda.time.DateTime

trait IncomingFriendRequestsProjection

class DataStoreIncomingFriendRequestsProjection(dataStore: DataStore) extends IncomingFriendRequestsProjection with EventProcessor {
  override def process(event: Event): Unit = event match {
    case FriendRequestSent(sender, userId, id) =>
      id.foreach { _ =>
        val lastSequenceNum = dataStore.lastSequenceNum(userId)
        CommandProcessor.retry {
          Either.cond(dataStore.saveUserEvents(userId, lastSequenceNum, DateTime.now(), List(
            FriendRequestReceived(userId, sender, id)
          )), (), DbErrorEventsNotSaved)
        }
      }
    case _ =>
  }
}
