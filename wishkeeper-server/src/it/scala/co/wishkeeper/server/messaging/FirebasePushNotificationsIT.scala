package co.wishkeeper.server.messaging

import java.util.UUID
import java.util.UUID.randomUUID

import co.wishkeeper.server.NotificationsData.FriendRequestNotification
import co.wishkeeper.server.PushNotification
import org.specs2.mutable.Specification

class FirebasePushNotificationsIT extends Specification {
  "Should send a message successfully" in {
    val pushNotifications = new FirebasePushNotifications
    val pushNotification = PushNotification(UUID.randomUUID(), UUID.randomUUID(), FriendRequestNotification(randomUUID(), randomUUID()))
    val token = "dXAmPeCoa24:APA91bFQmQfq5v-Rm40FwbDGcoLcmtuP2ehbYVP9URP4EqICN_j8K91QhIoBrs4sJ68SKwo8cVN85-Sck4U6UdDo9YoUMUz3E3dcM9ZaOfvWucALHn7tDlNwzNKurLhMaumXH1AhnU2vTJVH9hN_yLLiX212gWV2aw"
    pushNotifications.send(token, pushNotification) must beSuccessfulTry
  }
}
