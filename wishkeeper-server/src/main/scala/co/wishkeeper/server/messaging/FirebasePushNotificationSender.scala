package co.wishkeeper.server.messaging

import java.util.concurrent.Executors.newFixedThreadPool

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.stream.ActorMaterializer
import co.wishkeeper.server.NotificationsData.UserNotification
import co.wishkeeper.server.user.Platform
import co.wishkeeper.server.{BroadcastNotification, NotificationsData, PushNotification, TypedJson, UserNotifications}
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.messaging.{AndroidConfig, ApnsConfig, Aps, ApsAlert, FirebaseMessaging, Message, Notification}
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import io.circe.generic.auto._
import io.circe.parser._
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.fromExecutorService
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait PushNotificationSender {
  def send(deviceToken: String, notification: PushNotification, platform: Platform = Platform.Android): Try[String]

  def sendToTopic(topic: String, notification: BroadcastNotification, platform: Platform = Platform.Android): Try[String]
}

trait TopicManager {
  def subscribeTo(topic: String, deviceIds: List[String]): Future[Unit]

  def unsubscribeFrom(topic: String, deviceIds: List[String]): Future[Unit]
}

object PushNotificationSender {
  val periodicWakeup = "periodic-wakeup"
}

class FirebasePushNotificationSender(executionContext: ExecutionContext = fromExecutorService(newFixedThreadPool(5)), config: FirebaseConfig)
                                    (implicit system: ActorSystem, am: ActorMaterializer) extends PushNotificationSender with TopicManager {

  import FirebasePushNotificationSender._

  implicit val ec = executionContext

  private val credentials: GoogleCredentials = GoogleCredentials.getApplicationDefault
  private val firebaseOptions: FirebaseOptions = new FirebaseOptions.Builder().setCredentials(credentials).build()
  FirebaseApp.initializeApp(firebaseOptions)

  private val logger = LoggerFactory.getLogger(classOf[PushNotificationSender])

  private val androidConfig = AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build()

  private def createPushMessage(notification: PushNotification, configure: Message.Builder => Message.Builder,
                                platform: Platform)(implicit typedJson: TypedJson[PushNotification]): Message = {

    val builder = Message.builder().putData("body", typedJson.toJson(notification))

    platform match {
      case Platform.Android => configure(builder.setAndroidConfig(androidConfig)).build()
      case Platform.iOS =>
        notification.data match {
          case n: UserNotification =>
            configure(builder
              .setNotification(new Notification(n.title, n.content)))
              .setApnsConfig(ApnsConfig.builder()
                .setAps(Aps.builder()
                  .setSound("default")
                  .setAlert(ApsAlert.builder()
                    .setTitle(n.title)
                    .setBody(n.content)
                    .build())
                  .build())
                .build())
              .build()
          case _ => configure(builder).build()
        }
    }
  }

  private def createMessage[T](notification: T, configure: Message.Builder => Message.Builder, platform: Platform)(implicit typedJson: TypedJson[T]) = {
    val messageBuilder = Message.builder()
      .putData("body", typedJson.toJson(notification))
      .setAndroidConfig(androidConfig)

    configure(messageBuilder).build()
  }

  private val send: Message => Try[String] = msg => {
    val result = Try(FirebaseMessaging.getInstance().send(msg))
    result.recover({ case t: Throwable => logger.error("Error sending notification", t) })
    result
  }

  def send(deviceToken: String, notification: PushNotification, platform: Platform): Try[String] =
    send(createPushMessage(notification, _.setToken(deviceToken), platform))

  def sendToTopic(topic: String, notification: BroadcastNotification, platform: Platform): Try[String] =
    send(createMessage(notification, _.setTopic(topic), platform))

  def subscribeTo(topic: String, deviceIds: List[String]): Future[Unit] = Future {
    deviceIds.grouped(MaxRegistrationDevicesPerRequest).foreach { ids =>
      val response = FirebaseMessaging.getInstance().subscribeToTopic(ids.asJava, topic)
      if (response.getFailureCount > 0) // TODO remove this, just for debug. Reconciliation mechanism will do it instead
        response.getErrors.asScala.map(_.getReason).foreach(println)
    }
  }

  def unsubscribeFrom(topic: String, deviceIds: List[String]): Future[Unit] = Future {
    deviceIds.grouped(MaxRegistrationDevicesPerRequest).foreach { ids =>
      val response = FirebaseMessaging.getInstance().unsubscribeFromTopic(ids.asJava, topic)
      if (response.getFailureCount > 0) // TODO remove this, just for debug. Reconciliation mechanism will do it instead
        response.getErrors.asScala.map(_.getReason).foreach(println)
    }
  }

  def subscriptionsFor(deviceId: String): Future[List[String]] = {
    import akka.http.scaladsl.model.headers._

    Http().singleRequest(HttpRequest(
      uri = s"https://iid.googleapis.com/iid/info/$deviceId?details=true",
      headers = List(RawHeader("Authorization", s"key=${config.cloudMessagingServerKey}"))
    )).flatMap(response => {
      if (response.status.isSuccess())
        Future.successful(response.entity)
      else
        response.entity.toStrict(timeout)
          .flatMap(_.dataBytes.runFold("")(_ + _.utf8String))
          .flatMap(err => Future.failed(new RuntimeException(err)))
    }).flatMap(_.toStrict(timeout))
      .flatMap(_.dataBytes.runFold("")(_ + _.utf8String))
      .map(json => {
        decode[FirebaseInstanceIdResult](json).map(_.rel.topics.keys.toList).getOrElse(Nil)
      })
  }
}

object FirebasePushNotificationSender {
  val MaxRegistrationDevicesPerRequest = 1000
  val timeout = 20.seconds
}

case class FirebaseInstanceIdResult(rel: FirebaseInstanceRelationships)

case class FirebaseInstanceRelationships(topics: Map[String, Map[String, String]])

case class FirebaseConfig(cloudMessagingServerKey: String)