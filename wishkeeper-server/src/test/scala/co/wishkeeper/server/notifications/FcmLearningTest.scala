package co.wishkeeper.server.notifications

import java.util.UUID

import co.wishkeeper.server.NotificationsData.{FriendRequestAcceptedNotification, FriendRequestNotification}
import co.wishkeeper.server.messaging.FirebasePushNotifications
import co.wishkeeper.server.{PushNotification, UserProfile}
import org.specs2.mutable.Specification

class FcmLearningTest extends Specification {
  "sending a message" in {
//    val push = new FirebasePushNotifications
//    val token = "fmC4f-u0uNs:APA91bFx3WyfBPAjAhk1mu-i3CjsE0ZN87xLRW_uc2yMOyk6TA-OHoTCGfqoryMxtimNhceHw4dF4TfQo3OLaKk5uWaCDv7gYDuMStuNlbDB5XSuF7FjhiCMauFucZnepRVITMl52VHmuLOEJP3W6t7WY3_RdHbltw"
    //    push.send(token, PushNotification(WishUnreservedNotification(UUID.fromString("5379d611-bfb8-4545-aa5d-ce6263f0cc9a"), UUID.randomUUID(), wishName = Option("Hipster Deer Print"))))
//    push.send(token, PushNotification(FriendRequestNotification(
//      UUID.fromString("20fffc34-f54a-4d1c-a965-84b80c36d5a7"),
//      UUID.fromString("1cdc4fd0-6c69-4596-86de-e5b21d0ff1c6"),
//      profile = Option(UserProfile(
//        name = Option("Ullrich Albffifibkad Wongson"),
//        firstName = Option("Ullrich"),
//        picture = Option("https://platform-lookaside.fbsbx.com/platform/profilepic/?asid=106534476914307&height=80&width=80&ext=1530892560&hash=AeQvUMuLFVGpuot-")))))
//    ) must beASuccessfulTry
//    push.send(token, PushNotification(FriendRequestAcceptedNotification(
//      UUID.fromString("20fffc34-f54a-4d1c-a965-84b80c36d5a7"),
//      UUID.fromString("1cdc4fd0-6c69-4596-86de-e5b21d0ff1c6"),
//      Option(UserProfile(
//        name = Option("Ullrich Albffifibkad Wongson"),
//        firstName = Option("Ullrich"),
//        picture = Option("https://platform-lookaside.fbsbx.com/platform/profilepic/?asid=106534476914307&height=80&width=80&ext=1530892560&hash=AeQvUMuLFVGpuot-")))))
//    ) must beASuccessfulTry
    ok
  }
}