package co.wishkeeper.server.api

import java.util.UUID
import java.util.UUID.randomUUID

import co.wishkeeper.server.Events.{UserPictureSet, WishImageSet}
import co.wishkeeper.server.EventsTestHelper.EventsList
import co.wishkeeper.server.image.ContentTypes
import co.wishkeeper.server.image.ContentTypes.jpeg
import co.wishkeeper.server.{CommandProcessor, DataStore, ImageLink, ImageLinks}
import co.wishkeeper.server.user.commands.{DeleteUserPicture, SetFlagFacebookFriendsListSeen}
import com.wixpress.common.specs2.JMock
import org.joda.time.DateTime
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class DelegatingManagementApiTest extends Specification with JMock {

  "should apply the reset flag facebook friends seen event" in new Context {

    checking {
      oneOf(commandProcessor).process(SetFlagFacebookFriendsListSeen(false), userId)
    }

    api.resetFacebookFriendsSeenFlag(userId)
  }

  "should delete user picture" in new Context {

    checking {
      oneOf(commandProcessor).validatedProcess(DeleteUserPicture, userId).willReturn(Right(()))
    }

    api.deleteUserPicture(userId)
  }

  "should invoke image https url migration" in new Context {
    val user1Id: UUID = randomUUID()
    val user2Id: UUID = randomUUID()
    val wish1Id = randomUUID()
    val wish2Id = randomUUID()
    val lastSeqNum = Option(100L)

    checking {
      allowing(dataStore).userEmails.willReturn(List("email1" -> user1Id, "email2" -> user2Id).iterator)
      allowing(dataStore).userEvents(user1Id).willReturn(EventsList(user1Id)
        .withPic(s"http://user.media.wishkeeper.co/abcd1234")
        .withWish(wish1Id, "wish")
        .withEvent(WishImageSet(wish1Id, ImageLinks(List(ImageLink("http://wish.media.wishkeeper.co/lkjhfdsa", 100, 100, jpeg)))))
        .list)
      allowing(dataStore).userEvents(user2Id).willReturn(EventsList(user2Id)
        .withPic(s"http://user.media.wishkeeper.co/qwerty321")
        .withWish(wish2Id, "wish")
        .withEvent(WishImageSet(wish2Id, ImageLinks(List(ImageLink("http://wish.media.wishkeeper.co/popopopopo", 100, 100, jpeg)))))
        .list)
      allowing(dataStore).lastSequenceNum(user1Id).willReturn(lastSeqNum)
      allowing(dataStore).lastSequenceNum(user2Id).willReturn(lastSeqNum)
      oneOf(dataStore).saveUserEvents(having(===(user1Id)), having(===(lastSeqNum)), having(any[DateTime]), having(beEqualTo(List(
        UserPictureSet(user1Id, "https://user-media.wishkeeper.co/abcd1234"),
        WishImageSet(wish1Id, ImageLinks(List(ImageLink("https://wish-media.wishkeeper.co/lkjhfdsa", 100, 100, jpeg))))
      )))).willReturn(true)
      oneOf(dataStore).saveUserEvents(having(===(user2Id)), having(===(lastSeqNum)), having(any[DateTime]), having(beEqualTo(List(
        UserPictureSet(user2Id, "https://user-media.wishkeeper.co/qwerty321"),
        WishImageSet(wish2Id, ImageLinks(List(ImageLink("https://wish-media.wishkeeper.co/popopopopo", 100, 100, jpeg))))
      )))).willReturn(true)
    }

    api.migrateUrlsToHttp()
  }

  trait Context extends Scope {
    val userId: UUID = randomUUID()
    val commandProcessor: CommandProcessor = mock[CommandProcessor]
    val dataStore = mock[DataStore]
    val api = new DelegatingManagementApi(null, null, dataStore, commandProcessor,
      null, null, null)
  }

}
