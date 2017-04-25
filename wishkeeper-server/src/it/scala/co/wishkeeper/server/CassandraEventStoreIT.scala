package co.wishkeeper.server

import java.util.UUID.randomUUID

import co.wishkeeper.EventStoreTestHelper
import co.wishkeeper.server.Events.UserConnected
import org.joda.time.DateTime
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

class CassandraEventStoreIT extends FlatSpec with Matchers with BeforeAndAfterAll {

  var eventStore: EventStore = _
  val userId = randomUUID()
  val sessionId = randomUUID()

  it should "save a user event" in {
    val event = UserConnected(userId, DateTime.now(), sessionId)
    eventStore.saveUserEvents(userId, None, DateTime.now(), List(event))
    eventStore.userEventsFor(userId) should contain(event)
  }

  it should "save a user session" in {
    eventStore.saveUserSession(userId, sessionId, DateTime.now())
    eventStore.userBySession(sessionId) shouldBe Some(userId)
  }

  override protected def beforeAll(): Unit = {
    CassandraDocker.start()
    EventStoreTestHelper().createSchema()

    eventStore = new CassandraEventStore
  }
}