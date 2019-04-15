package co.wishkeeper.server

import java.util.UUID

import org.specs2.mutable.Specification

class StaticFileFeatureTogglesTest extends Specification {
  "return true if user id is in test users list" in {
    val testUsers = List(
      UUID.fromString("cde53805-683a-439e-91ee-597838793b5b"),
      UUID.fromString("0cc31801-50a0-4fa7-8242-9f3bd9e519c5"))
    val toggles = new StaticFileFeatureToggles()
    testUsers.map(toggles.isTestUser) must contain(exactly(true, true))
  }

  "return false if user id not in test users list" in {
    val toggles = new StaticFileFeatureToggles()
    toggles.isTestUser(UUID.randomUUID()) must beFalse
  }
}
