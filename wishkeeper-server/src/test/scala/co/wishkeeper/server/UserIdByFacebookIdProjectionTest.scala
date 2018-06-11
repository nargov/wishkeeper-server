package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.Events.{Event, NoOp, UserFacebookIdSet}
import co.wishkeeper.server.projections.{DataStoreUserIdByFacebookIdProjection, UserIdByFacebookIdProjection}
import com.wixpress.common.specs2.JMock
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class UserIdByFacebookIdProjectionTest extends Specification with JMock {

  trait Context extends Scope {
    val dataStore = mock[DataStore]
    val facebookIdProjection: UserIdByFacebookIdProjection with EventProcessor = new DataStoreUserIdByFacebookIdProjection(dataStore)
    val userId = UUID.randomUUID()
    val facebookId = "facebook-id"
  }

  "create new data store entry on facebook id event" in new Context {

    checking {
      oneOf(dataStore).saveUserIdByFacebookId(facebookId, userId)
    }

    facebookIdProjection.process(UserFacebookIdSet(userId, facebookId), userId) must beEmpty
  }

  "ignore irrelevant events" in new Context {
    checking {
      never(dataStore).saveUserIdByFacebookId(having(any[String]), having(any[UUID]))
    }

    facebookIdProjection.process(NoOp, userId) must beEmpty
  }

  "return a list of users for given list of facebook ids" in new Context {
    val anotherFacebookId = "another-facebook-id"
    val anotherUserId = UUID.randomUUID()
    val facebookIds = List(facebookId, anotherFacebookId)
    val expectedMap: Map[String, UUID] = Map(facebookId -> userId, anotherFacebookId -> anotherUserId)

    checking {
      allowing(dataStore).userIdsByFacebookIds(facebookIds).willReturn(expectedMap)
    }

    facebookIdProjection.get(facebookIds) must beEqualTo(expectedMap)
  }
}
