package co.wishkeeper.server.notifications

import java.util.UUID

import co.wishkeeper.server.Events.DeviceNotificationIdSet
import co.wishkeeper.server.messaging.{PushNotificationSender, TopicManager}
import co.wishkeeper.server._

class DeviceIdEventProcessor(topicManager: TopicManager, dataStore: DataStore) extends EventProcessor {
  def resubscribeAll(): Unit = {
    val deviceIds = dataStore.allUserEvents(classOf[DeviceNotificationIdSet]).map {
      case UserEventInstance(_, Events.DeviceNotificationIdSet(id), _) => id
    }
    topicManager.subscribeTo(PushNotificationSender.periodicWakeup, deviceIds.toList)
  }

  override def process(event: Events.Event, userId: UUID): List[(UUID, Events.Event)] = {
    event match {
      case DeviceNotificationIdSet(id) => topicManager.subscribeTo(PushNotificationSender.periodicWakeup, id :: Nil)
      case _ =>
    }
    Nil
  }
}
