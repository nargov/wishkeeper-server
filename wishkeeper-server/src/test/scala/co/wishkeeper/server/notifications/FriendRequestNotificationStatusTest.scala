package co.wishkeeper.server.notifications

import co.wishkeeper.server.FriendRequestNotificationStatus
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import io.circe.syntax._
import org.specs2.mutable.Specification

class FriendRequestNotificationStatusTest extends Specification {

  implicit val config = Configuration.default.withDefaults

  case class Tester(status: FriendRequestNotificationStatus)
  val tester = Tester(FriendRequestNotificationStatus.Pending)

  "should render to json as string" in {
    tester.asJson.noSpaces must beEqualTo("""{"status":"Pending"}""")
  }
}
