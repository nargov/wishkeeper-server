package co.wishkeeper.server.messaging

import java.util.concurrent.atomic.AtomicReference

import com.google.cloud.ServiceOptions
import com.google.cloud.pubsub.v1.{SubscriptionAdminClient, TopicAdminClient}
import com.google.pubsub.v1.{ProjectName, ProjectSubscriptionName, ProjectTopicName}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import org.specs2.specification.{BeforeAfterAll, Scope}

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random

class GooglePubSubMessageBusIT(implicit ee: ExecutionEnv) extends Specification with BeforeAfterAll {
  sequential

  val topic = "test-topic"
  val projectId: String = ServiceOptions.getDefaultProjectId

  "consumer receives message that producer sends" in new Context {
    subscribeTo("test-subscription")

    sendMessage()

    lastMessage mustContainMessage expectedMessage
  }


  "multiple consumers receive message that producer sends" in new Context {
    val lastMessage2 = new AtomicReference[String]()
    subscribeTo("test-subscription-2")
    subscribeTo("test-subscription-3", lastMessage2)

    sendMessage()

    lastMessage mustContainMessage expectedMessage
    lastMessage2 mustContainMessage expectedMessage
  }


  override def beforeAll(): Unit = {
    val topicName = ProjectTopicName.of(projectId, topic)
    val topicAdminClient = TopicAdminClient.create()
    if (!topicAdminClient.listTopics(ProjectName.newBuilder().setProject(projectId).build()).iterateAll().asScala.exists(_.getName == topicName.toString)) {
      println("creating topic")
      topicAdminClient.createTopic(topicName)
    }
  }

  override def afterAll(): Unit = {
    val subscriptionAdminClient = SubscriptionAdminClient.create()
    List("test-subscription", "test-subscription-2", "test-subscription-3").foreach { sub =>
      subscriptionAdminClient.deleteSubscription(ProjectSubscriptionName.of(projectId, sub))
    }
  }

  trait Context extends Scope {
    def subscribeTo(subscription: String, messageStore: AtomicReference[String] = lastMessage) = {
      new GooglePubSubMessageBusConsumer(topic, subscription, msg => messageStore.set(msg))
    }

    def sendMessage() = {
      val eventualSend: Future[Unit] = producer.send(expectedMessage)
      eventualSend must ===(()).await(20, 200.millis)
    }

    val expectedMessage = s"test-message-${Random.nextInt(1000)}"
    val lastMessage = new AtomicReference[String]()
    val producer: MessageBusProducer = new GooglePubSubMessageBusProducer(topic)

    implicit class AtomicRefOps(atomicReference: AtomicReference[String]) {
      def mustContainMessage(message: String) =
        atomicReference.get() must beEqualTo(message).eventually(50, 100.millis)
    }

  }

}
