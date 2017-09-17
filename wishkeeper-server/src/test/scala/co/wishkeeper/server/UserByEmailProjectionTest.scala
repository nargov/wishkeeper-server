package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.Events.UserEmailSet
import com.wixpress.common.specs2.JMock
import org.specs2.mutable.Specification

class UserByEmailProjectionTest extends Specification with JMock {

  "should save user by email for saved email" in {
    val dataStore = mock[DataStore]
    val email = "email@example.com"
    val userId = UUID.randomUUID()

    checking {
      oneOf(dataStore).saveUserByEmail(email, userId)
    }

    new UserByEmailProjection(dataStore).process(UserEmailSet(userId, email))
  }
}
