package co.wishkeeper.server

import java.util.UUID

import akka.http.scaladsl.testkit.Specs2RouteTest
import com.wixpress.common.specs2.JMock
import org.specs2.mutable.Specification
import io.circe.generic.auto._
import de.heikoseeberger.akkahttpcirce.CirceSupport._


class RouteTest extends Specification with Specs2RouteTest with JMock {
  "Management Route" should {
    "Return a user profile" in {
      val userProfileProjection = mock[UserProfileProjection]

      val webApi = new WebApi(null, null, null, userProfileProjection)
      val name = "Joe"

      checking {
        allowing(userProfileProjection).get(having(any[UUID])).willReturn(UserProfile(name = Option(name)))
      }

      Get(s"/users/${UUID.randomUUID()}/profile") ~> webApi.managementRoute ~> check {
        handled should beTrue
        responseAs[UserProfile].name should beSome(name)
      }
    }
  }
}
