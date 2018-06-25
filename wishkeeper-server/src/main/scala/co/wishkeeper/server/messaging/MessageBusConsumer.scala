package co.wishkeeper.server.messaging

import com.google.api.core.{ApiFuture, ApiFutureCallback, ApiFutures}
import com.google.cloud.ServiceOptions
import com.google.cloud.pubsub.v1._
import com.google.protobuf.ByteString
import com.google.pubsub.v1._

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}

trait MessageBusConsumer

trait MessageBusProducer {
  def send(message: String): Future[Unit]
}

class GooglePubSubMessageBusConsumer(topic: String,
                                     subscription: String,
                                     onMessage: String => Unit,
                                     ackDeadline: Duration = 30.seconds) extends MessageBusConsumer {

  private val projectId: String = ServiceOptions.getDefaultProjectId
  private val projectName = ProjectName.of(projectId)
  private val topicName = ProjectTopicName.of(projectId, topic)
  private val subscriptionName: ProjectSubscriptionName = ProjectSubscriptionName.of(projectId, subscription)
  private val subscriptionAdminClient: SubscriptionAdminClient = SubscriptionAdminClient.create()

  if (!subscriptionAdminClient.listSubscriptions(projectName).iterateAll().asScala.exists(_.getName == subscriptionName.toString)) {
    subscriptionAdminClient.createSubscription(subscriptionName, topicName, PushConfig.newBuilder().build(), ackDeadline.toSeconds.toInt)
  }

  private val subscriber = Subscriber.newBuilder(subscriptionName, new MessageReceiver {
    override def receiveMessage(message: PubsubMessage, consumer: AckReplyConsumer): Unit = {
      onMessage(message.getData.toStringUtf8)
      consumer.ack()
    }
  }).build()
  subscriber.startAsync().awaitRunning()
}

class GooglePubSubMessageBusProducer(topic: String) extends MessageBusProducer {

  private val projectId: String = ServiceOptions.getDefaultProjectId
  private val topicName = ProjectTopicName.of(projectId, topic)
  private val publisher = Publisher.newBuilder(topicName).build()

  override def send(message: String): Future[Unit] = {
    val p = Promise[Unit]()
    val futureResult: ApiFuture[String] = publisher.publish(PubsubMessage.newBuilder().setData(ByteString.copyFromUtf8(message)).build())
    ApiFutures.addCallback(futureResult, new ApiFutureCallback[String] {
      override def onFailure(t: Throwable): Unit = {
        t.printStackTrace()
        p.failure(t)
      }

      override def onSuccess(result: String): Unit = p.success(())
    })
    p.future
  }
}