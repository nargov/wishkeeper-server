package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.Events.{UserConnected, UserNameSet}
import co.wishkeeper.server.projections.{ReplayingUserProfileProjection, UserProfileProjection}
import com.wixpress.common.specs2.JMock
import org.joda.time.DateTime
import org.specs2.mutable.Specification

class UserProfileProjectionTest extends Specification with JMock {
  "return a user profile" in {
    val dataStore = mock[DataStore]
    val projection: UserProfileProjection = new ReplayingUserProfileProjection(dataStore)

    val userId = UUID.randomUUID()
    val joe = "Joe"

    checking {
      allowing(dataStore).userEventsFor(userId).willReturn(List(UserConnected(userId, DateTime.now(), UUID.randomUUID()), UserNameSet(userId, joe)))
    }

    projection.get(userId) must beEqualTo(UserProfile(name = Some(joe)))
  }
}
