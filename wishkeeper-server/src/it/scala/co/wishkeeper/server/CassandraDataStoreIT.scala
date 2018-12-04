package co.wishkeeper.server

import java.util.UUID
import java.util.UUID.randomUUID

import co.wishkeeper.DataStoreTestHelper
import co.wishkeeper.server.Events.{UserConnected, UserNameSet}
import co.wishkeeper.server.EventsTestHelper.EventsList
import co.wishkeeper.server.image.ContentTypes
import co.wishkeeper.server.user.{EmailTokenAlreadyVerified, VerificationToken}
import co.wishkeeper.server.user.events.history.{HistoryEventInstance, ReceivedWish, ReservedWish}
import org.joda.time.DateTime
import org.scalatest.enablers.Containing
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

class CassandraDataStoreIT extends FlatSpec with Matchers with BeforeAndAfterAll {

  var dataStore: CassandraDataStore = _
  val userId = randomUUID()
  val sessionId = randomUUID()

  val firstName = "Joe"
  val lastName = "Shmoe"
  val name = firstName + " " + lastName
  val picture = Option("Picture")

  it should "save a user event" in {
    val time = DateTime.now()
    val event = UserConnected(userId, time, sessionId)
    dataStore.saveUserEvents(userId, None, time, List(event))
    dataStore.userEvents(userId) should contain(UserEventInstant(event, time))
  }

  it should "save a user session" in {
    dataStore.saveUserSession(userId, sessionId, DateTime.now())
    dataStore.userBySession(sessionId) shouldBe Some(userId)
  }

  it should "return a user id for a given facebook id" in {
    val facebookId = "facebook-id"
    dataStore.saveUserIdByFacebookId(facebookId, userId) shouldBe true
    dataStore.userIdByFacebookId(facebookId) shouldBe Some(userId)
  }

  it should "return None for a non existing facebook id" in {
    dataStore.userIdByFacebookId("some-non-existing-id") shouldBe None
  }

  it should "return a map of facebook ids to user ids" in {
    val userId1 = randomUUID()
    val userId2 = randomUUID()
    val facebookId1 = "fbid-1"
    val facebookId2 = "fbid-2"
    dataStore.saveUserIdByFacebookId(facebookId1, userId1) shouldBe true
    dataStore.saveUserIdByFacebookId(facebookId2, userId2) shouldBe true

    dataStore.userIdsByFacebookIds(List(facebookId1, facebookId2)) shouldBe Map(facebookId1 -> userId1, facebookId2 -> userId2)
  }

  it should "return false on concurrent modification" in {
    val maybeLastSeqNum = dataStore.lastSequenceNum(userId)
    val events = List(UserConnected(userId, sessionId = randomUUID()))
    dataStore.saveUserEvents(userId, maybeLastSeqNum, DateTime.now(), events) shouldBe true
    dataStore.saveUserEvents(userId, maybeLastSeqNum, DateTime.now(), events) shouldBe false
  }

  it should "return a user by email" in {
    val email = "zaphod@beeblebrox.com"
    dataStore.saveUserByEmail(email, userId)
    dataStore.userIdByEmail(email) shouldBe Some(userId)
  }

  it should "Save a user by name" in {
    val expectedRow = UserNameSearchRow(userId, name, picture, Option(firstName), Option(lastName))

    dataStore.saveUserByName(expectedRow)
    dataStore.userNames() should contain(expectedRow)
  }

  it should "Save a user by name without picture" in {
    val initialRow = UserNameSearchRow(userId, name, picture, Option(firstName), Option(lastName))
    val expectedRow = initialRow.copy(picture = None)

    dataStore.saveUserByName(initialRow) shouldBe true
    dataStore.saveUserByName(expectedRow) shouldBe true
    dataStore.userNames() should contain(expectedRow)
  }

  it should "Save multiple users by name" in {
    val expectedRows = List(
      UserNameSearchRow(userId, name, picture, Option(firstName), Option(lastName)),
      UserNameSearchRow(randomUUID(), "Roger Wilco", Option("roger's pic"), Option("Roger"), Option("Wilco"))
    )

    dataStore.saveUserByName(expectedRows)
    dataStore.userNames() should contain allElementsOf expectedRows
  }

  it should "Return all user events of given types" in {
    val user1Events = EventsList(randomUUID()).withName("Joe").withWish(randomUUID(), "wish")
    val user2Events = EventsList(randomUUID()).withName("Bob").withFriend(randomUUID()).withPic("pic")

    (user1Events :: user2Events :: Nil).foreach(events =>
      dataStore.saveUserEvents(events.userId, dataStore.lastSequenceNum(events.userId), DateTime.now(), events.list.map(_.event)) shouldBe true)

    dataStore.allUserEvents(classOf[UserNameSet]).map(i => (i.userId, i.event)).toList should contain allElementsOf List(
      (user1Events.userId, UserNameSet(user1Events.userId, "Joe")),
      (user2Events.userId, UserNameSet(user2Events.userId, "Bob"))
    )
  }

  it should "Save history event" in {
    val time = DateTime.now()
    val wishId = randomUUID()
    val historyEvent = ReservedWish(wishId, randomUUID(), "owner", "the wish", Option(ImageLinks(List(ImageLink("url", 1, 1, ContentTypes.jpeg)))))
    val historyEventInstance = HistoryEventInstance(userId, wishId, time, historyEvent)
    dataStore.saveUserHistoryEvent(userId, time, historyEvent, wishId) shouldBe true
    dataStore.historyFor(userId) should contain(historyEventInstance)
  }

  it should "Truncate history table" in {
    dataStore.saveUserHistoryEvent(userId, DateTime.now(), ReservedWish(randomUUID(), randomUUID(), "", "", None), randomUUID()) shouldBe true
    dataStore.truncateHistory()
    dataStore.historyFor(userId) shouldBe empty
  }

  it should "delete history event" in {
    val wishId = randomUUID()
    dataStore.saveUserHistoryEvent(userId, DateTime.now(), ReservedWish(wishId, randomUUID(), "", "", None), wishId) shouldBe true
    dataStore.saveUserHistoryEvent(userId, DateTime.now(), ReceivedWish(randomUUID(), randomUUID(), "", "other", None), randomUUID()) shouldBe true
    dataStore.historyFor(userId) should have size 2
    dataStore.deleteWishHistoryEvent(userId, wishId)
    dataStore.historyFor(userId) should have size 1
  }

  it should "return all user id and emails" in {
    implicit val containsEntry: Containing[Map[String, UUID]] = new Containing[Map[String, UUID]] {
      override def contains(container: Map[String, UUID], element: Any): Boolean = element match {
        case e: String => container.contains(e)
        case _ => false
      }

      override def containsOneOf(container: Map[String, UUID], elements: Seq[Any]): Boolean = ???

      override def containsNoneOf(container: Map[String, UUID], elements: Seq[Any]): Boolean = ???
    }
    val userId1 = randomUUID()
    val userId2 = randomUUID()
    dataStore.saveUserByEmail("a", userId1)
    dataStore.saveUserByEmail("b", userId2)
    val emails = dataStore.userEmails.toMap
    emails.exists(e => e._1 == "a" && e._2 == userId1) shouldBe true
    emails.exists(e => e._1 == "b" && e._2 == userId2) shouldBe true
  }

  it should "store an email verification token" in {
    val token = VerificationToken(randomUUID(), "email@address.com", randomUUID())
    dataStore.saveVerificationToken(token) shouldBe Right(true)
    dataStore.readVerificationToken(token.token) shouldBe Right(token)
  }

  it should "set verification token to verified" in {
    val token = VerificationToken(randomUUID(), "email@address.com", randomUUID())
    dataStore.saveVerificationToken(token) shouldBe Right(true)
    dataStore.verifyEmailToken(token.token) shouldBe Right(token.copy(verified = true))
  }

  it should "truncate the user by name table" in {
    dataStore.saveUserByName(UserNameSearchRow(randomUUID(), "name"))
    dataStore.truncateUserByName()
    dataStore.userNames() should have size 0
  }

  it should "fail marking email verified if already verified" in {
    val token = randomUUID()
    val verificationToken = VerificationToken(token, "test.email@example.com", randomUUID())
    dataStore.saveVerificationToken(verificationToken)
    dataStore.verifyEmailToken(token) shouldBe Right(verificationToken.copy(verified = true))
    dataStore.verifyEmailToken(token) shouldBe Left(EmailTokenAlreadyVerified)
  }

  val dataStoreTestHelper = DataStoreTestHelper()

  override protected def beforeAll(): Unit = {
    CassandraDocker.start()
    dataStoreTestHelper.start()
    dataStoreTestHelper.createSchema()

    dataStore = new CassandraDataStore(DataStoreConfig(dataStoreTestHelper.nodeAddresses))
    dataStore.connect()
  }

  override protected def afterAll(): Unit = {
    dataStore.close()
    dataStoreTestHelper.stop()
  }
}