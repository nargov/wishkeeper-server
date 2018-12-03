package co.wishkeeper.server.search

import java.util.UUID
import java.util.UUID.randomUUID

import co.wishkeeper.server.Events._
import co.wishkeeper.server.EventsTestHelper.{EventsList, asEventInstants}
import co.wishkeeper.server.{DataStore, UserEventInstance, UserNameSearchRow}
import com.wixpress.common.specs2.JMock
import org.specs2.matcher.Matcher
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class SimpleScanUserSearchProjectionTest extends Specification with JMock {
  "User Search Projection" should {
    "return users matching query" in new Context {
      searchFor("Joe") must contain(exactly(aUser(joeId)))
    }

    "return matches ignoring case" in new Context {
      searchFor("joe") must contain(exactly(aUser(joeId)))
    }

    "return multiple matches" in new Context {
      searchFor("jo") must contain(exactly(aUser(joeId), aUser(jonathanId), aUser(bobbyboobriId)))
    }

    "return first name matches before last name matches" in new Context {
      searchFor("bri") must contain(exactly(aUser(brianId), aUser(bobbyboobriId), aUser(bobbyId)).inOrder)
    }

    "return last name matches before full name matches" in new Context {
      searchFor("ay") must contain(exactly(aUser(brianId), aUser(philId)).inOrder)
    }

    "Save a user's name for search" in new Context {
      checking {
        allowing(dataStore).userEvents(joeId).willReturn(EventsList(joeId).list)
        oneOf(dataStore).saveUserByName(UserNameSearchRow(joeId, joeFullName))
      }

      searchProjection.process(UserEventInstance(joeId, UserNameSet(joeId, joeFullName)))
    }

    "Save a user's name along with existing data" in new Context {
      checking {
        allowing(dataStore).userEvents(joeId).willReturn(
          EventsList(joeId).withFirstName("Joe").withLastName("Satriani").withPic("pic").list)
        oneOf(dataStore).saveUserByName(UserNameSearchRow(joeId, joeFullName, Option("pic"), Option("Joe"), Option("Satriani")))
      }

      searchProjection.process(UserEventInstance(joeId, UserNameSet(joeId, joeFullName)))
    }

    "Save a user's first name for search" in new Context {
      checking {
        allowing(dataStore).userEvents(joeId).willReturn(
          EventsList(joeId).withName(joeFullName).withLastName("Satriani").withPic("pic").list)
        oneOf(dataStore).saveUserByName(UserNameSearchRow(joeId, joeFullName, Option("pic"), Option("Joe"), Option("Satriani")))
      }

      searchProjection.process(UserEventInstance(joeId, UserFirstNameSet(joeId, "Joe")))
    }

    "Save a user's last name for search" in new Context {
      checking {
        allowing(dataStore).userEvents(joeId).willReturn(
          EventsList(joeId).withName(joeFullName).withFirstName("Joe").withPic("pic").list)
        oneOf(dataStore).saveUserByName(UserNameSearchRow(joeId, joeFullName, Option("pic"), Option("Joe"), Option("Satriani")))
      }

      searchProjection.process(UserEventInstance(joeId, UserLastNameSet(joeId, "Satriani")))
    }

    "Save a user's picture for search" in new Context {
      checking {
        allowing(dataStore).userEvents(joeId).willReturn(
          EventsList(joeId).withName(joeFullName).withFirstName("Joe").withLastName("Satriani").withPic("pic").list)
        oneOf(dataStore).saveUserByName(UserNameSearchRow(joeId, joeFullName, Option("newPic"), Option("Joe"), Option("Satriani")))
      }

      searchProjection.process(UserEventInstance(joeId, UserPictureSet(joeId, "newPic")))
    }

    "Save name as first name if name is missing" in new Context {
      checking {
        allowing(dataStore).userEvents(joeId).willReturn(EventsList(joeId).list)
        oneOf(dataStore).saveUserByName(UserNameSearchRow(joeId, "Joe", None, Option("Joe"), None))
      }

      searchProjection.process(UserEventInstance(joeId, UserFirstNameSet(joeId, "Joe")))
    }

    "Save name as last name if name is missing" in new Context {
      checking {
        allowing(dataStore).userEvents(joeId).willReturn(EventsList(joeId).list)
        oneOf(dataStore).saveUserByName(UserNameSearchRow(joeId, "Satriani", None, None, Option("Satriani")))
      }

      searchProjection.process(UserEventInstance(joeId, UserLastNameSet(joeId, "Satriani")))
    }

    "Rebuild the view" in new Context {
      val bobLastName = "Dylan"
      val bobName = s"Bob $bobLastName"
      val jonathanName = "Jonathan Strange"

      checking {
        allowing(dataStore).userEmails.willReturn(List(joeEmail -> joeId, "bobbyEmail" -> bobbyId, "jonathanEmail" -> jonathanId).iterator)
        allowing(dataStore).userEvents(joeId).will(returnValue(EventsList(joeId).withName(joeFullName).withPic(joePic).list))
        allowing(dataStore).userEvents(bobbyId).willReturn(EventsList(bobbyId).withName(bobName).withLastName(bobLastName).list)
        allowing(dataStore).userEvents(jonathanId).willReturn(EventsList(jonathanId).withName(jonathanName).list)
        oneOf(dataStore).saveUserByName(UserNameSearchRow(joeId, joeFullName, Option(joePic)))
        oneOf(dataStore).saveUserByName(UserNameSearchRow(bobbyId, bobName, lastName = Option(bobLastName)))
        oneOf(dataStore).saveUserByName(UserNameSearchRow(jonathanId, jonathanName))
      }

      searchProjection.rebuild()
    }

    "return matches for multiple query terms" in new Context {
      searchFor("bb jo") must contain(exactly(aUser(bobbyboobriId)))
    }

    "Return a users's details and whether is direct friend" in new Context {
      searchFor("bb jo") must contain(exactly(
        UserSearchResult(bobbyboobriId, "Bobbyboobri Jones", firstName = Option("Bobbyboobri"), isDirectFriend = true)))
    }

    "Not add user to search if never connected" in new Context {
      checking {
        never(dataStore).saveUserByName(having(any[UserNameSearchRow]))
        never(dataStore).saveUserByName(having(any[List[UserNameSearchRow]]))
        allowing(dataStore).userEvents(joeId).willReturn(asEventInstants(List(EmailConnectStarted(joeId))))
      }

      searchProjection.process(UserEventInstance(joeId, UserNameSet(joeId, joeFullName)))
    }

    "Not add user to search if never connected on rebuild" in new Context {
      checking {
        allowing(dataStore).userEmails.willReturn(List(joeEmail -> joeId).iterator)
        allowing(dataStore).userEvents(joeId).willReturn(EventsList(joeId).withEvent(EmailConnectStarted(joeId)).withEmail(joeEmail).list)
        never(dataStore).saveUserByName(having[List[UserNameSearchRow]](contain(aRowFor(joeId))))
      }
      searchProjection.rebuild()
    }
  }

  def aRowFor(id: UUID): Matcher[UserNameSearchRow] = ===(id) ^^ ((_: UserNameSearchRow).userId)

  def aUser(id: UUID): Matcher[UserSearchResult] = ===(id) ^^ ((_: UserSearchResult).userId)

  trait Context extends Scope {
    val userId = randomUUID()
    val joeId = randomUUID()
    val joeEmail = "joeEmail"
    val bobbyId = randomUUID()
    val bobbyboobriId = randomUUID()
    val jonathanId = randomUUID()
    val brianId = randomUUID()
    val philId = randomUUID()
    val dataStore = mock[DataStore]
    val searchProjection = new SimpleScanUserSearchProjection(dataStore)
    val joeFullName = "Joe Satriani"
    val joePic = "Joe's picture"

    def searchFor(term: String): List[UserSearchResult] = {
      searchProjection.byName(userId, term).users
    }

    checking {
      allowing(dataStore).userNames().willReturn(List(
        UserNameSearchRow(joeId, joeFullName, Option(joePic), Option("Joe"), Option("Satriani")),
        UserNameSearchRow(bobbyId, "Bobby Briggs", None, Option("Bobby"), Option("Briggs")),
        UserNameSearchRow(bobbyboobriId, "Bobbyboobri Jones", None, Option("Bobbyboobri"), Option("Jones")),
        UserNameSearchRow(jonathanId, "Jonathan Price", None, Option("Jonathan"), Option("Price")),
        UserNameSearchRow(brianId, "Brian May", Option("Brian's pic"), Option("Brian"), Option("May")),
        UserNameSearchRow(philId, "Phil Ayer", None, Option("Phil"), None)
      ))
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId).withFriend(bobbyboobriId).list)
    }
  }

}
