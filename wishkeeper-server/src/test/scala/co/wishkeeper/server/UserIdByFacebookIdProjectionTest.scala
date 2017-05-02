package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.Events.{Event, NoOp, UserFacebookIdSet}
import com.wixpress.common.specs2.JMock
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class UserIdByFacebookIdProjectionTest extends Specification with JMock {

  trait Context extends Scope {
    val dataStore = mock[DataStore]
    val facebookIdProjection = new UserIdByFacebookIdProjection(dataStore)
    val userId = UUID.randomUUID()
    val facebookId = "facebook-id"
  }

  "create new data store entry on facebook id event" in new Context {

    checking {
      oneOf(dataStore).saveUserIdByFacebookId(facebookId, userId)
    }

    facebookIdProjection.process(UserFacebookIdSet(userId, facebookId))
  }

  "ignore irrelevant events" in new Context {
    checking {
      never(dataStore).saveUserIdByFacebookId(having(any[String]), having(any[UUID]))
    }

    facebookIdProjection.process(NoOp)
  }

  "return a user id for a given facebook id" in new Context {
    checking {
      allowing(dataStore).userIdByFacebookId(facebookId).willReturn(Some(userId))
    }

    facebookIdProjection.get(facebookId) must beSome(userId)
  }
}
