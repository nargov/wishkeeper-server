package co.wishkeeper.server.notifications

import java.util.UUID
import java.util.UUID.randomUUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicReference

import co.wishkeeper.server.DataStore
import co.wishkeeper.server.Events.WishReservedNotificationCreated
import co.wishkeeper.server.EventsTestHelper.EventsList
import co.wishkeeper.server.messaging.{MemStateClientRegistry, NotificationsUpdated, ServerNotification}
import com.wixpress.common.specs2.JMock
import org.jmock.lib.concurrent.DeterministicScheduler
import org.joda.time.DateTime
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import scala.concurrent.duration._

class DelayedNotificationComponentTest extends Specification with JMock {
  "Reserving a wish should send a delayed notification to a connected client" in new Context {
    assumingNoPendingNotifications()

    userConnects()

    serverNotificationEventProcessor.process(WishReservedNotificationCreated(randomUUID(), randomUUID(), randomUUID()), userId)

    scheduler.tick(config.default.toSeconds - 1, SECONDS)
    message.get() must beNull

    scheduler.tick(1, SECONDS)

    message.get() must beEqualTo(ServerNotification.toJson(NotificationsUpdated))
  }

  "User connecting having an existing reserved wish notification pending should get a notification in time" in new Context {
    assumingPendingReservedWishNotification()

    userConnects()

    scheduler.tick(2, SECONDS)
    message.get() must beNull

    scheduler.tick(1, SECONDS)

    message.get() must beEqualTo(ServerNotification.toJson(NotificationsUpdated))
  }

  "Connecting user should receive multiple notifications" in new Context {
    checking {
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId).
        withReservedWishNotification(randomUUID(), randomUUID(), randomUUID(), DateTime.now().minusSeconds(2)).
        withReservedWishNotification(randomUUID(), randomUUID(), randomUUID(), DateTime.now().minusSeconds(3)).
        list)
    }

    val messages = new ArrayBlockingQueue[String](2)
    clientRegistry.add(userId, messages.add)

    scheduler.tick(2, SECONDS)
    messages.size() must beEqualTo(1)

    scheduler.tick(1, SECONDS)
    messages.size() must beEqualTo(2)

  }

  trait Context extends Scope {
    val dataStore = mock[DataStore]
    val scheduler = new DeterministicScheduler
    val clientRegistry = new MemStateClientRegistry()
    val config = NotificationDelayConfig(default = 5.seconds)
    val notificationsScheduler = new ExecutorNotificationsScheduler(config, scheduler, clientRegistry, dataStore)
    val serverNotificationEventProcessor = new ServerNotificationEventProcessor(clientRegistry, notificationsScheduler)
    val message = new AtomicReference[String]()
    val userId = randomUUID()

    def assumingPendingReservedWishNotification() = checking {
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId).
        withReservedWishNotification(randomUUID(), randomUUID(), randomUUID(), DateTime.now().minusSeconds(2)).list)
    }

    def assumingNoPendingNotifications() = {
      checking {
        allowing(dataStore).userEvents(userId).willReturn(EventsList(userId).list)
      }
    }

    def userConnects() = clientRegistry.add(userId, message.set)

    def userDisconnects(connection: UUID) = clientRegistry.remove(userId, connection)
  }

}
