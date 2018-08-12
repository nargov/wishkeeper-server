package co.wishkeeper.server.search

import java.util.UUID
import java.util.UUID.randomUUID

import co.wishkeeper.server.Events._
import co.wishkeeper.server.EventsTestHelper.{EventsList, asEventInstances}
import co.wishkeeper.server.{DataStore, UserNameSearchRow}
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
      searchFor("jo")must contain(exactly(aUser(joeId), aUser(jonathanId), aUser(bobbyboobriId)))
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

      searchProjection.process(UserNameSet(joeId, joeFullName), joeId)
    }

    "Save a user's name along with existing data" in new Context {
      checking {
        allowing(dataStore).userEvents(joeId).willReturn(
          EventsList(joeId).withFirstName("Joe").withLastName("Satriani").withPic("pic").list)
        oneOf(dataStore).saveUserByName(UserNameSearchRow(joeId, joeFullName, Option("pic"), Option("Joe"), Option("Satriani")))
      }

      searchProjection.process(UserNameSet(joeId, joeFullName), joeId)
    }

    "Save a user's first name for search" in new Context {
      checking {
        allowing(dataStore).userEvents(joeId).willReturn(
          EventsList(joeId).withName(joeFullName).withLastName("Satriani").withPic("pic").list)
        oneOf(dataStore).saveUserByName(UserNameSearchRow(joeId, joeFullName, Option("pic"), Option("Joe"), Option("Satriani")))
      }

      searchProjection.process(UserFirstNameSet(joeId, "Joe"), joeId)
    }

    "Save a user's last name for search" in new Context {
      checking {
        allowing(dataStore).userEvents(joeId).willReturn(
          EventsList(joeId).withName(joeFullName).withFirstName("Joe").withPic("pic").list)
        oneOf(dataStore).saveUserByName(UserNameSearchRow(joeId, joeFullName, Option("pic"), Option("Joe"), Option("Satriani")))
      }

      searchProjection.process(UserLastNameSet(joeId, "Satriani"), joeId)
    }

    "Save a user's picture for search" in new Context {
      checking {
        allowing(dataStore).userEvents(joeId).willReturn(
          EventsList(joeId).withName(joeFullName).withFirstName("Joe").withLastName("Satriani").withPic("pic").list)
        oneOf(dataStore).saveUserByName(UserNameSearchRow(joeId, joeFullName, Option("newPic"), Option("Joe"), Option("Satriani")))
      }

      searchProjection.process(UserPictureSet(joeId, "newPic"), joeId)
    }

    "Save name as first name if name is missing" in new Context {
      checking {
        allowing(dataStore).userEvents(joeId).willReturn(EventsList(joeId).list)
        oneOf(dataStore).saveUserByName(UserNameSearchRow(joeId, "Joe", None, Option("Joe"), None))
      }

      searchProjection.process(UserFirstNameSet(joeId, "Joe"), joeId)
    }

    "Save name as last name if name is missing" in new Context {
      checking {
        allowing(dataStore).userEvents(joeId).willReturn(EventsList(joeId).list)
        oneOf(dataStore).saveUserByName(UserNameSearchRow(joeId, "Satriani", None, None, Option("Satriani")))
      }

      searchProjection.process(UserLastNameSet(joeId, "Satriani"), joeId)
    }

    "Rebuild the view" in new Context {
      val joePic = "Joe's pic"
      val bobLastName = "Dylan"
      val bobName = s"Bob $bobLastName"
      val jonathanName = "Jonathan Strange"
      checking {
        allowing(dataStore).allUserEvents(classOf[UserNameSet], classOf[UserFirstNameSet], classOf[UserLastNameSet], classOf[UserPictureSet])
          .willReturn(asEventInstances(List(
            (joeId, UserNameSet(joeId, joeFullName)),
            (bobbyId, UserNameSet(bobbyId, bobName)),
            (jonathanId, UserNameSet(jonathanId, jonathanName)),
            (joeId, UserPictureSet(joeId, joePic)),
            (bobbyId, UserLastNameSet(bobbyId, bobLastName))
          )).toIterator)
        oneOf(dataStore).saveUserByName(List(
          UserNameSearchRow(joeId, joeFullName, Option(joePic)),
          UserNameSearchRow(bobbyId, bobName, lastName = Option(bobLastName)),
          UserNameSearchRow(jonathanId, jonathanName)
        ))
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
  }

  def aUser(id: UUID): Matcher[UserSearchResult] = ===(id) ^^ ((_: UserSearchResult).userId)

  trait Context extends Scope {
    val userId = randomUUID()
    val joeId = randomUUID()
    val bobbyId = randomUUID()
    val bobbyboobriId = randomUUID()
    val jonathanId = randomUUID()
    val brianId = randomUUID()
    val philId = randomUUID()
    val dataStore = mock[DataStore]
    val searchProjection = new SimpleScanUserSearchProjection(dataStore)
    val joeFullName = "Joe Satriani"

    def searchFor(term: String): List[UserSearchResult] = {
      searchProjection.byName(userId, term).users
    }

    checking {
      allowing(dataStore).userNames().willReturn(List(
        UserNameSearchRow(joeId, joeFullName, Option("Joe's picture"), Option("Joe"), Option("Satriani")),
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
