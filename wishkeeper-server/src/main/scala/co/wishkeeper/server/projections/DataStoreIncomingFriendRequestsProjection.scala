package co.wishkeeper.server.projections

import java.util.UUID

import co.wishkeeper.server.Events.{Event, FriendRequestNotificationCreated, FriendRequestReceived, FriendRequestSent}
import co.wishkeeper.server.{DataStore, EventProcessor, User}
import org.joda.time.DateTime

class DataStoreIncomingFriendRequestsProjection(dataStore: DataStore) extends IncomingFriendRequestsProjection with EventProcessor {
  def awaitingApproval(userId: UUID): List[UUID] = User.replay(dataStore.userEvents(userId)).friends.requestReceived

  override def process(event: Event): Unit = {
    event match {
      case FriendRequestSent(sender, userId) =>
        val lastSequenceNum = dataStore.lastSequenceNum(userId)
        dataStore.saveUserEvents(userId, lastSequenceNum, DateTime.now(), List(
          FriendRequestReceived(userId, sender),
          FriendRequestNotificationCreated(UUID.randomUUID(), userId, sender)
        ))
      case _ =>
    }
  }
}


trait IncomingFriendRequestsProjection {
  def awaitingApproval(userId: UUID): List[UUID]
}

