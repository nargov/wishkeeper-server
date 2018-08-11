package co.wishkeeper.server

import java.util.UUID.randomUUID

import co.wishkeeper.DataStoreTestHelper
import co.wishkeeper.server.Events.{UserConnected, UserNameSet}
import co.wishkeeper.server.EventsTestHelper.EventsList
import org.joda.time.DateTime
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

class CassandraDataStoreIT extends FlatSpec with Matchers with BeforeAndAfterAll {

  var eventStore: CassandraDataStore = _
  val userId = randomUUID()
  val sessionId = randomUUID()

  val firstName = "Joe"
  val lastName = "Shmoe"
  val name = firstName + " " + lastName
  val picture = Option("Picture")

  it should "save a user event" in {
    val time = DateTime.now()
    val event = UserConnected(userId, time, sessionId)
    eventStore.saveUserEvents(userId, None, time, List(event))
    eventStore.userEvents(userId) should contain(UserEventInstant(event, time))
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

  it should "return a user by email" in {
    val email = "zaphod@beeblebrox.com"
    eventStore.saveUserByEmail(email, userId)
    eventStore.userByEmail(email) shouldBe Some(userId)
  }

  it should "Save a user by name" in {
    val expectedRow = UserNameSearchRow(userId, name, picture, Option(firstName), Option(lastName))

    eventStore.saveUserByName(expectedRow)
    eventStore.userNames() should contain(expectedRow)
  }

  it should "Save a user by name without picture" in {
    val initialRow = UserNameSearchRow(userId, name, picture, Option(firstName), Option(lastName))
    val expectedRow = initialRow.copy(picture = None)

    eventStore.saveUserByName(initialRow) shouldBe true
    eventStore.saveUserByName(expectedRow) shouldBe true
    eventStore.userNames() should contain(expectedRow)
  }

  it should "Save multiple users by name" in {
    val expectedRows = List(
      UserNameSearchRow(userId, name, picture, Option(firstName), Option(lastName)),
      UserNameSearchRow(randomUUID(), "Roger Wilco", Option("roger's pic"), Option("Roger"), Option("Wilco"))
    )

    eventStore.saveUserByName(expectedRows)
    eventStore.userNames() should contain allElementsOf expectedRows
  }

  it should "Return all user events of given types" in {
    val user1Events = EventsList(randomUUID()).withName("Joe").withWish(randomUUID(), "wish")
    val user2Events = EventsList(randomUUID()).withName("Bob").withFriend(randomUUID()).withPic("pic")

    (user1Events :: user2Events :: Nil).foreach(events =>
      eventStore.saveUserEvents(events.userId, eventStore.lastSequenceNum(events.userId), DateTime.now(), events.list.map(_.event)) shouldBe true)

    eventStore.allUserEvents(classOf[UserNameSet]).map(i => (i.userId, i.event)).toList should contain allElementsOf List(
      (user1Events.userId, UserNameSet(user1Events.userId, "Joe")),
      (user2Events.userId, UserNameSet(user2Events.userId, "Bob"))
    )
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