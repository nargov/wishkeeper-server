package co.wishkeeper.server.messaging

import co.wishkeeper.server.PushNotification
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.messaging.{AndroidConfig, FirebaseMessaging, Message}
import com.google.firebase.{FirebaseApp, FirebaseOptions}

import scala.util.Try

trait PushNotifications {
  def send(deviceToken: String, notification: PushNotification): Try[String]
}

class FirebasePushNotifications extends PushNotifications {
  FirebaseApp.initializeApp(new FirebaseOptions.Builder().setCredentials(GoogleCredentials.getApplicationDefault).build())

  def send(deviceToken: String, notification: PushNotification): Try[String] = {
    val androidConfig = AndroidConfig.builder().build()
    val message = Message.builder().
      putData("body", notification.toJson).
      setToken(deviceToken).
      setAndroidConfig(androidConfig).
      build()
    Try(FirebaseMessaging.getInstance().send(message))
  }
}
