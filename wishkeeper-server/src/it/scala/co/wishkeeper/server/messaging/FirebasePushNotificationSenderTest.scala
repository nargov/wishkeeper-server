package co.wishkeeper.server.messaging

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import scala.concurrent.duration._

// ATTENTION: To run this test, you'll have to specify the key for the firebase cloud messaging service as an env variable.
// The key you're looking for is the legacy server key from the firebase cloud messaging configuration.

class FirebasePushNotificationSenderTest(implicit ee: ExecutionEnv) extends Specification {
  sequential

  implicit val system = ActorSystem("test-system")
  implicit val am = ActorMaterializer()
  val config: Config = ConfigFactory.load()
  val push = new FirebasePushNotificationSender(config = FirebaseConfig(config.getString("wishkeeper.fcm.key")))

  "Should subscribe to topic" in new Context {
    val topic = "test-topic"
    val futureSubscription = push.subscribeTo(topic, deviceId :: Nil)
    futureSubscription must beEqualTo(()).await(100, 200.millis)

    val eventualTopics = push.subscriptionsFor(deviceId)
    eventualTopics must containTheSameElementsAs(topic :: Nil).await(100, 200.millis)
  }

  "Should unsubscribe from topic" in new Context {
    val topic = "test-topic2"
    val futureSubscription = push.subscribeTo(topic, deviceId :: Nil)
    futureSubscription must beEqualTo(()).await(100, 100.millis)

    val futureUnsubscription = push.unsubscribeFrom(topic, deviceId :: Nil)
    futureUnsubscription must beEqualTo(()).await(100, 100.millis)

    val eventualTopics = push.subscriptionsFor(deviceId)
    eventualTopics must not(contain(allOf(topic))).await(100, 100.millis)
  }

  trait Context extends Scope {
    val deviceId = "dFqzUEHy80I:APA91bHynoE74jvdAEu59wI9km2noXrTj9iOe4dn_mxqR53Q9XnJxtKPfBkeGv69MSq0YuPPhrdlJIwHkZ" +
      "5yai7tF8pDXW-wx3xDsyovYBwN5XykIqu-NY7AEoHKO-38aiKOahpbvnqwGTOfhgo5HpoFjE0xCoFalw"
  }
}
