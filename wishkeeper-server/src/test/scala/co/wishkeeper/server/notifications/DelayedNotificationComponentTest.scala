package co.wishkeeper.server.notifications

import java.util.UUID
import java.util.UUID.randomUUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicReference

import co.wishkeeper.server.Events.{DeviceNotificationIdSet, WishReservedNotificationCreated, WishUnreservedNotificationCreated}
import co.wishkeeper.server.EventsTestHelper.EventsList
import co.wishkeeper.server.NotificationsData.{WishReservedNotification, WishUnreservedNotification}
import co.wishkeeper.server.messaging.{MemStateClientRegistry, NotificationsUpdated, PushNotifications, ServerNotification}
import co.wishkeeper.server.{DataStore, PushNotification, UserProfile}
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

    assertGotNotificationsUpdated()
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

  "Wish Reserved Notification should be sent through push channel" in new Context {
    val event = WishReservedNotificationCreated(randomUUID(), randomUUID(), randomUUID())

    val wishName = "My Wish"
    val pushNotification = PushNotification(WishReservedNotification(event.wishId, event.reserverId, wishName = Option(wishName)))

    checking {
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId).withEvent(idSet).withWish(event.wishId, wishName).list)
      oneOf(pushNotifications).send(idSet.id, pushNotification)
    }

    userConnects()

    serverNotificationEventProcessor.process(event, userId)
    scheduler.tick(config.default.toSeconds, SECONDS)

    assertGotNotificationsUpdated()
  }

  "Wish Unreserved Notification should be sent after delay to push channel" in new Context {
    val wishId = randomUUID()
    val friendId = randomUUID()
    val event = WishUnreservedNotificationCreated(randomUUID(), wishId)
    val wishName = "Wish Name"
    val pushNotification = PushNotification(WishUnreservedNotification(event.wishId, null, wishName = Option(wishName)))

    checking {
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId).withEvent(idSet).withReservedWish(wishId, wishName, friendId).list)
      oneOf(pushNotifications).send(idSet.id, pushNotification)
    }

    userConnects()

    serverNotificationEventProcessor.process(event, userId)
    scheduler.tick(config.default.toSeconds, SECONDS)

    assertGotNotificationsUpdated()
  }


  trait Context extends Scope {
    val dataStore = mock[DataStore]
    val pushNotifications = mock[PushNotifications]
    val scheduler = new DeterministicScheduler
    val clientRegistry = new MemStateClientRegistry()
    val config = NotificationDelayConfig(default = 5.seconds)
    val notificationsScheduler = new ExecutorNotificationsScheduler(config, scheduler, clientRegistry, dataStore, pushNotifications)
    val serverNotificationEventProcessor = new ServerNotificationEventProcessor(clientRegistry, notificationsScheduler, dataStore, pushNotifications)
    val message = new AtomicReference[String]()
    val userId = randomUUID()
    val idSet = DeviceNotificationIdSet("id")

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

    def assertGotNotificationsUpdated() = {
      message.get() must beEqualTo(ServerNotification.toJson(NotificationsUpdated))
    }
  }
}
