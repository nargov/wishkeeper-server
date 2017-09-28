package co.wishkeeper.server.notifications

import co.wishkeeper.server.FriendRequestStatus
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import io.circe.syntax._
import io.circe.parser._
import org.specs2.mutable.Specification

class FriendRequestStatusTest extends Specification {

  implicit val config = Configuration.default.withDefaults

  case class Tester(status: FriendRequestStatus)
  val tester = Tester(FriendRequestStatus.Pending)
  val testString = """{"status":"Pending"}"""

  "should render to json as string" in {
    tester.asJson.noSpaces must beEqualTo(testString)
  }

  "should decode from json" in {
    decode[Tester](testString) must beRight(tester)
  }
}
