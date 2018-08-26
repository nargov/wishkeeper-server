package co.wishkeeper.server.notifications

import java.util.UUID.randomUUID

import co.wishkeeper.server.Events.DeviceNotificationIdSet
import co.wishkeeper.server.messaging.{PushNotificationSender, TopicManager}
import co.wishkeeper.server.{DataStore, EventProcessor, UserEventInstance}
import com.wixpress.common.specs2.JMock
import org.joda.time.DateTime
import org.specs2.mutable.Spec

class DeviceIdEventProcessorTest extends Spec with JMock {
  "register a device to periodic wakeup topic" in {
    val topicManager = mock[TopicManager]
    val dataStore = mock[DataStore]
    val processor: EventProcessor = new DeviceIdEventProcessor(topicManager, dataStore)
    val event = DeviceNotificationIdSet("device id")

    checking {
      oneOf(topicManager).subscribeTo(PushNotificationSender.periodicWakeup, event.id :: Nil)
    }

    processor.process(event, randomUUID())
  }

  "register all devices" in {
    val topicManager = mock[TopicManager]
    val dataStore = mock[DataStore]
    val processor = new DeviceIdEventProcessor(topicManager, dataStore)
    val registrationEvents = List(
      UserEventInstance(randomUUID(), DeviceNotificationIdSet("a"), DateTime.now()),
      UserEventInstance(randomUUID(), DeviceNotificationIdSet("b"), DateTime.now()),
      UserEventInstance(randomUUID(), DeviceNotificationIdSet("c"), DateTime.now())
    )

    checking {
      allowing(dataStore).allUserEvents(classOf[DeviceNotificationIdSet]).willReturn(registrationEvents.iterator)
      oneOf(topicManager).subscribeTo(PushNotificationSender.periodicWakeup, registrationEvents.map(_.event.id))
    }

    processor.resubscribeAll()
  }

}
