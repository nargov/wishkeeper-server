package co.wishkeeper.server.projections

import java.util.UUID
import java.util.UUID.randomUUID

import co.wishkeeper.server.Events.{WishGranted, WishImageSet, WishReserved, WishUnreserved}
import co.wishkeeper.server.EventsTestHelper.EventsList
import co.wishkeeper.server._
import co.wishkeeper.server.messaging.{ClientNotifier, HistoryUpdated}
import co.wishkeeper.server.user.events.history.{GrantedWish, HistoryEventInstance, ReceivedWish, ReservedWish}
import com.wixpress.common.specs2.JMock
import org.joda.time.DateTime
import org.specs2.mutable.Spec
import org.specs2.specification.Scope

class ScanningUserHistoryProjectionTest extends Spec with JMock {
  "Saves user history event for reserving a wish" in new DataStoreContext {
    checking {
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId)
        .withWish(wishId, wishName)
        .withName(userName)
        .withEvent(WishImageSet(wishId, imageLinks))
        .list)
      oneOf(dataStore).saveUserHistoryEvent(having(===(reserver)), having(any[DateTime]),
        having(===(ReservedWish(wishId, userId, userName, wishName, maybeLinks))), having(===(wishId))).willReturn(true)
    }

    projection.process(UserEventInstance(userId, WishReserved(wishId, reserver)))
  }

  "Saves user history event for having granted a wish" in new DataStoreContext {
    checking {
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId)
        .withReservedWish(wishId, wishName, reserver)
        .withName(userName)
        .withEvent(WishImageSet(wishId, imageLinks))
        .withEvent(WishGranted(wishId))
        .list)
      ignoring(dataStore).saveUserHistoryEvent(having(any[UUID]), having(any[DateTime]), having(anInstanceOf[ReceivedWish]), having(any))
      oneOf(dataStore).deleteWishHistoryEvent(reserver, wishId)
      oneOf(dataStore).saveUserHistoryEvent(having(===(reserver)), having(any[DateTime]),
        having(===(GrantedWish(wishId, userId, userName, wishName, maybeLinks))), having(===(wishId))).willReturn(true)
    }

    projection.process(UserEventInstance(userId, WishGranted(wishId)))
  }

  "Saves user history event for having been granted a wish" in new DataStoreContext {
    checking {
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId)
        .withReservedWish(wishId, wishName, reserver)
        .withEvent(WishImageSet(wishId, imageLinks))
        .withEvent(WishGranted(wishId))
        .list)
      ignoring(dataStore).saveUserHistoryEvent(having(any[UUID]), having(any[DateTime]), having(anInstanceOf[GrantedWish]), having(any))
      ignoring(dataStore).deleteWishHistoryEvent(having(any), having(any))
      oneOf(dataStore).saveUserHistoryEvent(having(===(userId)), having(any[DateTime]),
        having(===(ReceivedWish(wishId, reserver, userName, wishName, maybeLinks))), having(===(wishId))).willReturn(true)
    }

    projection.process(UserEventInstance(userId, WishGranted(wishId)))
  }

  "Rebuilds projection" in new DataStoreContext {
    checking {
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId).withReservedWish(wishId, wishName, reserver).list)
      oneOf(dataStore).allUserEvents(classOf[WishReserved], classOf[WishUnreserved], classOf[WishGranted]).willReturn(
        List(UserEventInstance(userId, WishReserved(wishId, reserver), DateTime.now())).iterator)
      oneOf(dataStore).truncateHistory().willReturn(true)
      oneOf(dataStore).saveUserHistoryEvent(having(===(reserver)), having(any[DateTime]), having(anInstanceOf[ReservedWish]), having(===(wishId)))
    }

    projection.rebuild()
  }

  "Notify on added history event after wish reserved" in new Context {
    ignoringSaveHistory()

    checking {
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId).withWish(wishId, "name").list)
      oneOf(notifier).sendTo(HistoryUpdated, reserver)
    }

    projection.process(UserEventInstance(userId, WishReserved(wishId, reserver)))
  }

  "Notify on added history event after wish granted" in new Context {
    ignoringSaveHistory()

    checking {
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId).withReservedWish(wishId, "name", reserver).list)
      ignoring(dataStore).deleteWishHistoryEvent(having(any), having(any))
      oneOf(notifier).sendTo(HistoryUpdated, reserver)
      oneOf(notifier).sendTo(HistoryUpdated, userId)
    }

    projection.process(UserEventInstance(userId, WishGranted(wishId)))
  }

  "Deletes history event on reserved wish if was unreserved" in new DataStoreContext {
    checking {
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId).withReservedWish(wishId, "name", reserver).list)
      oneOf(dataStore).deleteWishHistoryEvent(reserver, wishId)
    }

    projection.process(UserEventInstance(userId, WishUnreserved(wishId)))
  }

  "Notify on deleted history event after unreserve" in new Context {
    val event = WishUnreserved(wishId)
    checking {
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId)
        .withReservedWish(wishId, "name", reserver)
        .withEvent(event)
        .list)
      ignoring(dataStore).deleteWishHistoryEvent(reserver, wishId)
      oneOf(notifier).sendTo(HistoryUpdated, reserver)
    }

    projection.process(UserEventInstance(userId, event))
  }

  "Returns an ordered list of history events" in new Context {
    val wish1 = randomUUID()
    val wish2 = randomUUID()
    val wish3 = randomUUID()
    checking {
      allowing(dataStore).historyFor(userId).willReturn(List(
        HistoryEventInstance(userId, wish3, DateTime.now().minusMinutes(2), ReservedWish(wish3, randomUUID(), "A", "A", None)),
        HistoryEventInstance(userId, wish1, DateTime.now(), ReservedWish(wish1, randomUUID(), "A", "A", None)),
        HistoryEventInstance(userId, wish2, DateTime.now().minusMinutes(1), ReservedWish(wish2, randomUUID(), "A", "A", None))
      ))
    }

    projection.historyFor(userId).map(_.wishId) must beEqualTo(List(wish1, wish2, wish3))
  }

  "Deletes history event after unreserve, taking into account older reserves" in new DataStoreContext {
    val anotherReserver = randomUUID()
    checking {
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId)
        .withReservedWish(wishId, "name", reserver)
        .withEvent(WishUnreserved(wishId))
        .withReservedWish(wishId, "name", anotherReserver)
        .withEvent(WishUnreserved(wishId))
        .list
      )
      oneOf(dataStore).deleteWishHistoryEvent(reserver, wishId)
      allowing(dataStore).deleteWishHistoryEvent(anotherReserver, wishId)
    }

    projection.process(UserEventInstance(userId, WishUnreserved(wishId)))
  }

  "return friend history" in new DataStoreContext {
    val receivedWishEvent = HistoryEventInstance(userId, wishId, DateTime.now(), ReceivedWish(wishId, randomUUID(), "B", "B", None))
    checking {
      allowing(dataStore).historyFor(userId).willReturn(List(
        HistoryEventInstance(userId, wishId, DateTime.now(), ReservedWish(randomUUID(), randomUUID(), "A", "A", None)),
        receivedWishEvent,
      ))
    }

    projection.friendHistory(userId) must contain(exactly(receivedWishEvent))
  }

  "saves history event for self granted wish" in new DataStoreContext {
    checking {
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId)
        .withName(userName)
        .withWish(wishId, wishName)
        .withEvent(WishImageSet(wishId, imageLinks))
        .withEvent(WishGranted(wishId))
        .list)
      ignoring(dataStore).saveUserHistoryEvent(having(any[UUID]), having(any[DateTime]), having(anInstanceOf[GrantedWish]), having(any))
      ignoring(dataStore).deleteWishHistoryEvent(having(any), having(any))
      oneOf(dataStore).saveUserHistoryEvent(having(===(userId)), having(any[DateTime]),
        having(===(ReceivedWish(wishId, userId, userName, wishName, maybeLinks))), having(===(wishId))).willReturn(true)
    }

    projection.process(UserEventInstance(userId, WishGranted(wishId)))
  }

  trait DataStoreContext extends Context {
    checking {
      ignoring(notifier)
    }
  }

  trait Context extends Scope {
    val dataStore = mock[DataStore]
    val notifier = mock[ClientNotifier]
    val projection: UserHistoryProjection with EventProcessor = new ScanningUserHistoryProjection(dataStore, notifier)
    val wishId: UUID = randomUUID()
    val reserver: UUID = randomUUID()
    val userId: UUID = randomUUID()
    val wishName = "Expected Wish"
    val imageLinks = ImageLinks(List(ImageLink("image-link-url", 1, 1, "image-content-type")))
    val maybeLinks = Option(imageLinks)
    val userName = "Bill Brown"

    def ignoringSaveHistory() = checking(ignoring(dataStore).saveUserHistoryEvent(having(any), having(any), having(any), having(any)))

    checking {
      allowing(dataStore).userEvents(reserver).willReturn(EventsList(reserver).withName(userName).list)
    }
  }

}