package co.wishkeeper.server.notifications

import co.wishkeeper.server.Events.{DeviceNotificationIdSet, UserEvent}
import co.wishkeeper.server._
import co.wishkeeper.server.messaging.{PushNotificationSender, TopicManager}

class DeviceIdEventProcessor(topicManager: TopicManager, dataStore: DataStore) extends EventProcessor {
  def resubscribeAll(): Unit = {
    val deviceIds = dataStore.allUserEvents(classOf[DeviceNotificationIdSet]).collect {
      case UserEventInstance(_, Events.DeviceNotificationIdSet(id), _) => id
    }
    topicManager.subscribeTo(PushNotificationSender.periodicWakeup, deviceIds.toList)
  }

  override def process[E <: UserEvent](instance: UserEventInstance[E]): List[UserEventInstance[_ <: UserEvent]] = {
    instance.event match {
      case DeviceNotificationIdSet(id) => topicManager.subscribeTo(PushNotificationSender.periodicWakeup, id :: Nil)
      case _ =>
    }
    Nil
  }
}
