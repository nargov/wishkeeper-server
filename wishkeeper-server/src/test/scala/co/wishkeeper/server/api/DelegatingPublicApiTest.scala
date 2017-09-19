package co.wishkeeper.server.api

import java.util.UUID
import java.util.UUID.randomUUID

import co.wishkeeper.server.{DataStore, SessionNotFoundException, UserEventInstant}
import co.wishkeeper.server.Events.{FacebookFriendsListSeen, UserConnected}
import com.wixpress.common.specs2.JMock
import org.joda.time.DateTime
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class DelegatingPublicApiTest extends Specification with JMock {

  trait Context extends Scope {
    val sessionId: UUID = randomUUID()
    val userId: UUID = randomUUID()
    val dataStore: DataStore = mock[DataStore]
    val api: PublicApi = new DelegatingPublicApi(null, dataStore, null, null, null, null, null)(null, null, null)
  }

  "returns the flags for user by the given session" in new Context {

    checking {
      allowing(dataStore).userBySession(sessionId).willReturn(Option(userId))
      allowing(dataStore).userEvents(userId).willReturn(List(
        UserEventInstant(UserConnected(userId, sessionId = sessionId), DateTime.now()),
        UserEventInstant(FacebookFriendsListSeen(), DateTime.now())
      ))
    }

    api.userFlagsFor(sessionId).seenFacebookFriendsList must beTrue
  }

  "throws a SessionNotFoundException if session is not found" in new Context {
    checking {
      allowing(dataStore).userBySession(sessionId).willReturn(None)
    }

    api.userFlagsFor(sessionId) must throwA[SessionNotFoundException]
  }
}
