package co.wishkeeper.server

import java.util.UUID.randomUUID

import co.wishkeeper.DataStoreTestHelper
import co.wishkeeper.server.Events.UserConnected
import org.joda.time.DateTime
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

class CassandraDataStoreIT extends FlatSpec with Matchers with BeforeAndAfterAll {

  var eventStore: DataStore = _
  val userId = randomUUID()
  val sessionId = randomUUID()

  it should "save a user event" in {
    val event = UserConnected(DateTime.now(), sessionId)
    eventStore.saveUserEvents(userId, None, DateTime.now(), List(event))
    eventStore.userEventsFor(userId) should contain(event)
  }

  it should "save a user session" in {
    eventStore.saveUserSession(userId, sessionId, DateTime.now())
    eventStore.userBySession(sessionId) shouldBe Some(userId)
  }

  it should "return a user id for a given facebook id" in {
    val facebookId = "facebook-id"
    eventStore.saveUserIdByFacebookId(facebookId, userId) shouldBe true
    eventStore.userIdByFacebookId(facebookId) shouldBe Some(userId)
  }

  override protected def beforeAll(): Unit = {
    CassandraDocker.start()
    DataStoreTestHelper().createSchema()

    eventStore = new CassandraDataStore
  }

  override protected def afterAll(): Unit = {
    eventStore.close()
  }
}