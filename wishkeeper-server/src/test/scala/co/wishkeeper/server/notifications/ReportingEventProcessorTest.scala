package co.wishkeeper.server.notifications

import java.util.UUID
import java.util.UUID.randomUUID
import java.util.concurrent.TimeUnit.SECONDS

import co.wishkeeper.server.{DataStore, UserEventInstance}
import co.wishkeeper.server.Events._
import co.wishkeeper.server.EventsTestHelper.EventsList
import co.wishkeeper.server.FriendRequestStatus.Approved
import co.wishkeeper.server.notifications.ReportingEventProcessor.delaySeconds
import co.wishkeeper.server.reporting.Reporter
import com.wixpress.common.specs2.JMock
import org.jmock.lib.concurrent.DeterministicScheduler
import org.joda.time.DateTime
import org.specs2.mutable.Spec
import org.specs2.specification.Scope

class ReportingEventProcessorTest extends Spec with JMock {
  "report new user" in new Context {
    val userConnected = UserConnected(userId, sessionId = randomUUID())
    val events = EventsList(userId, userConnected.time)

    checking {
      oneOf(reporter).report(UserFirstConnection(userId, userConnected.time, userName))
      allowing(dataStore).userEvents(userId).will(returnValue(events.withName(userName).list))
    }

    processor.process(UserEventInstance(userId, userConnected))
    scheduler.runUntilIdle()
  }

  "report new user waiting for name to be set" in new Context {
    val userConnected = UserConnected(userId, sessionId = randomUUID())
    val events = EventsList(userId, userConnected.time)

    checking {
      oneOf(reporter).report(UserFirstConnection(userId, userConnected.time, userName))
      allowing(dataStore).userEvents(userId).will(returnValue(events.list), returnValue(events.withName(userName).list))
    }

    processor.process(UserEventInstance(userId, userConnected))
    scheduler.tick(delaySeconds, SECONDS)
  }

  "report user added new wish" in new Context {
    val wishAdded = WishCreated(randomUUID(), userId, DateTime.now())
    val wishNameSet = WishNameSet(wishAdded.wishId, "expected wish name")
    val events = EventsList(userId).withName(userName).withEvent(wishAdded)

    checking {
      oneOf(reporter).report(UserAddedWish(userId, userName, wishAdded.creationTime, Option(wishNameSet.name)))
      allowing(dataStore).userEvents(userId).will(returnValue(events.withWish(wishAdded.wishId, wishNameSet.name).list))
    }

    processor.process(UserEventInstance(userId, wishAdded))
    scheduler.runUntilIdle()
  }

  "report user added new wish after waiting for name to be set" in new Context {
    val wishAdded = WishCreated(randomUUID(), userId, DateTime.now())
    val wishNameSet = WishNameSet(wishAdded.wishId, "expected wish name")
    val events = EventsList(userId).withName(userName).withEvent(wishAdded)

    checking {
      oneOf(reporter).report(UserAddedWish(userId, userName, wishAdded.creationTime, Option(wishNameSet.name)))
      allowing(dataStore).userEvents(userId).will(returnValue(events.list), returnValue(events.withEvent(wishNameSet).list))
    }

    processor.process(UserEventInstance(userId, wishAdded))
    scheduler.tick(delaySeconds, SECONDS)
  }

  "report wish reserved" in new Context {
    val reserverId: UUID = randomUUID()
    val wishReserved = WishReserved(randomUUID(), reserverId)
    val wishName = "expected wish"
    val events = EventsList(userId).withName(userName).withReservedWish(wishReserved.wishId, wishName, reserverId).list
    val reserverName = Option("Bob")

    checking {
      oneOf(reporter).report(WishWasReserved(wishReserved.wishId, Option(wishName), userId, userName, reserverId, reserverName))
      allowing(dataStore).userEvents(userId).willReturn(events)
      allowing(dataStore).userEvents(reserverId).willReturn(EventsList(reserverId).withName(reserverName).list)
    }

    processor.process(UserEventInstance(userId, wishReserved))
    scheduler.runUntilIdle()
  }

  "report friend approved" in new Context {
    val friendId = randomUUID()
    val friendName = Option("Jerry")
    val events = EventsList(userId).withName(userName).list
    val friendEvents = EventsList(friendId).withName(friendName).list

    checking {
      oneOf(reporter).report(UsersBecameFriends(userId, userName, friendId, friendName))
      allowing(dataStore).userEvents(userId).willReturn(events)
      allowing(dataStore).userEvents(friendId).willReturn(friendEvents)
    }

    processor.process(UserEventInstance(userId, FriendRequestStatusChanged(friendId, randomUUID(), userId, Approved)))
    scheduler.runUntilIdle()
  }

  "Not report user connection if not new user" in new Context {
    val userConnected = UserConnected(userId, sessionId = randomUUID())
    val events = EventsList(userId, DateTime.now().minusDays(1))

    checking {
      never(reporter).report(having(any[UserFirstConnection]))
      allowing(dataStore).userEvents(userId).will(returnValue(events.list))
    }

    processor.process(UserEventInstance(userId, userConnected))
    scheduler.tick(delaySeconds, SECONDS)
  }

  trait Context extends Scope {
    val dataStore = mock[DataStore]
    val reporter = mock[Reporter]
    val scheduler = new DeterministicScheduler
    val processor = new ReportingEventProcessor(reporter, dataStore, scheduler)
    val userId = randomUUID()
    val userName = Option("Leroy")
  }
}
