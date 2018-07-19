package co.wishkeeper.server.messaging

import co.wishkeeper.server.PushNotification
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.messaging.{AndroidConfig, FirebaseMessaging, Message}
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import org.slf4j.LoggerFactory

import scala.util.Try

trait PushNotifications {
  def send(deviceToken: String, notification: PushNotification): Try[String]
}

class FirebasePushNotifications extends PushNotifications {
  FirebaseApp.initializeApp(new FirebaseOptions.Builder().setCredentials(GoogleCredentials.getApplicationDefault).build())

  val logger = LoggerFactory.getLogger(classOf[PushNotifications])

  def send(deviceToken: String, notification: PushNotification): Try[String] = {
    val androidConfig = AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build()
    val body = notification.toJson
    val message = Message.builder().
      putData("body", body).
      setToken(deviceToken).
      setAndroidConfig(androidConfig).
      build()
    val result = Try(FirebaseMessaging.getInstance().send(message))
    result.recover({ case t: Throwable => logger.error("Error sending notification", t) })
    result
  }
}
