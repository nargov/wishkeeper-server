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
    val event = UserConnected(userId, DateTime.now(), sessionId)
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

  it should "return None for a non existing facebook id" in {
    eventStore.userIdByFacebookId("some-non-existing-id") shouldBe None
  }

  it should "return a map of facebook ids to user ids" in {
    val userId1 = randomUUID()
    val userId2 = randomUUID()
    val facebookId1 = "fbid-1"
    val facebookId2 = "fbid-2"
    eventStore.saveUserIdByFacebookId(facebookId1, userId1) shouldBe true
    eventStore.saveUserIdByFacebookId(facebookId2, userId2) shouldBe true

    eventStore.userIdsByFacebookIds(List(facebookId1, facebookId2)) shouldBe Map(facebookId1 -> userId1, facebookId2 -> userId2)
  }

  it should "return false on concurrent modification" in {
    val maybeLastSeqNum = eventStore.lastSequenceNum(userId)
    val events = List(UserConnected(userId, sessionId = randomUUID()))
    eventStore.saveUserEvents(userId, maybeLastSeqNum, DateTime.now(), events) shouldBe true
    eventStore.saveUserEvents(userId, maybeLastSeqNum, DateTime.now(), events) shouldBe false
  }

  val dataStoreTestHelper = DataStoreTestHelper()

  override protected def beforeAll(): Unit = {
    CassandraDocker.start()
    dataStoreTestHelper.start()
    dataStoreTestHelper.createSchema()

    eventStore = new CassandraDataStore(DataStoreConfig(dataStoreTestHelper.nodeAddresses))
    eventStore.connect()
  }

  override protected def afterAll(): Unit = {
    eventStore.close()
    dataStoreTestHelper.stop()
  }
}