package co.wishkeeper.server

import com.google.api.core.{ApiFuture, ApiFutureCallback, ApiFutures}
import com.google.cloud.ServiceOptions
import com.google.cloud.pubsub.v1._
import com.google.protobuf.ByteString
import com.google.pubsub.v1._
import org.specs2.mutable.{After, Specification}
import org.specs2.specification.Scope

import scala.concurrent.duration._

class GooglePubSubLearningIT extends Specification {
  "should get message" in new Scope with After {
    val projectId: String = ServiceOptions.getDefaultProjectId
    val topicName = ProjectTopicName.of(projectId, "test-topic")
    val subscriptionName: ProjectSubscriptionName = ProjectSubscriptionName.of(projectId, "test-sub")
    val subscriptionAdminClient: SubscriptionAdminClient = SubscriptionAdminClient.create()
    val topicAdminClient = TopicAdminClient.create()

    val subscription: Subscription = subscriptionAdminClient.createSubscription(subscriptionName, topicName,
      PushConfig.newBuilder().build(), 30)

    subscription.isInitialized must beTrue.eventually(40, 100.millis)

    val publisher = Publisher.newBuilder(topicName).build()
    var result: String = _
    val subscriber = Subscriber.newBuilder(subscriptionName, new MessageReceiver {
      override def receiveMessage(message: PubsubMessage, consumer: AckReplyConsumer): Unit = {
        result = message.getData.toStringUtf8
        consumer.ack()
      }
    }).build()
    subscriber.startAsync().awaitRunning()

    val expectedMessage = "test-message"
    val futureResult: ApiFuture[String] = publisher.publish(PubsubMessage.newBuilder().setData(ByteString.copyFromUtf8(expectedMessage)).build())
    ApiFutures.addCallback(futureResult, new ApiFutureCallback[String] {
      override def onFailure(t: Throwable): Unit = t.printStackTrace()
      override def onSuccess(result: String): Unit = println(s"Message id is $result")
    })

    result must beEqualTo(expectedMessage).eventually(40, 100.millis)

    override def after = {
      publisher.shutdown()
      subscriber.stopAsync().awaitTerminated()
      subscriptionAdminClient.deleteSubscription(subscriptionName)
      subscriptionAdminClient.close()
    }
  }
}
