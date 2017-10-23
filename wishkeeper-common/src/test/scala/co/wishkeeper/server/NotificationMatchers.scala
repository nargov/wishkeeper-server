package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.NotificationsData.{FriendRequestAcceptedNotification, FriendRequestNotification, NotificationData}
import org.specs2.matcher.{Matcher, MustThrownMatchers}

import scala.reflect.ClassTag

trait NotificationMatchers extends MustThrownMatchers {

  //noinspection MatchToPartialFunction
  def aFriendRequestAcceptedNotification(requestId: UUID, friendId: UUID): Matcher[NotificationData] =
    (data: NotificationData) => data match {
      case FriendRequestAcceptedNotification(by, reqId, _) =>
        (by == friendId && reqId == requestId, if (by == friendId) s"$reqId does not equal $requestId" else s"$by does not equal $friendId")
      case _ => (false, "Notification is not a FriendRequestAcceptedNotification")
    }

  //noinspection MatchToPartialFunction
  def aFriendRequestNotificationWithStatus(expectedStatus: FriendRequestStatus): Matcher[NotificationData] =
    (data: NotificationData) => data match {
      case FriendRequestNotification(_, _, status, _) =>
        (status == expectedStatus, s"Friend request notification status $status was not $expectedStatus")
      case _ => (false, "Notification is not a FriendRequestNotification")
    }

  def aNotificationType[T](implicit ct: ClassTag[T]): Matcher[Notification] = {
    ((_:Notification).data.getClass.isAssignableFrom(ct.runtimeClass), s"Notification is not of type ${ct.runtimeClass.getName}")
  }

  def aNotificationWith(matcher: Matcher[NotificationData]): Matcher[Notification] = (notification: Notification) =>
    matcher(createExpectable(notification.data))

}

object NotificationMatchers extends NotificationMatchers