package co.wishkeeper.server.api

import java.util.UUID
import java.util.UUID.randomUUID

import co.wishkeeper.server.Commands.{ChangeFriendRequestStatus, GrantWish, RemoveFriend}
import co.wishkeeper.server.Events.{FacebookFriendsListSeen, UserConnected, WishGranted}
import co.wishkeeper.server.EventsTestHelper.EventsList
import co.wishkeeper.server.FriendRequestStatus.{Approved, Ignored}
import co.wishkeeper.server.NotificationsData.{FriendRequestNotification, NotificationData}
import co.wishkeeper.server._
import co.wishkeeper.server.projections._
import com.wixpress.common.specs2.JMock
import org.joda.time.DateTime
import org.specs2.matcher.Matcher
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class DelegatingPublicApiTest extends Specification with JMock {

  "returns the flags for user by the given session" in new LoggedInContext {
    checking {
      allowing(dataStore).userEvents(userId).willReturn(List(
        UserEventInstant(UserConnected(userId, sessionId = sessionId), DateTime.now()),
        UserEventInstant(FacebookFriendsListSeen(), DateTime.now())
      ))
    }

    api.userFlagsFor(sessionId).seenFacebookFriendsList must beTrue
  }

  "throws a SessionNotFoundException if session is not found" in new Context {
    checking {
      allowing(dataStore).userBySession(sessionId).willReturn(None)
    }

    api.userFlagsFor(sessionId) must throwA[SessionNotFoundException]
  }

  "returns user notifications" in new LoggedInContext {
    val notifications = List(Notification(randomUUID(), notificationData))

    checking {
      allowing(notificationsProjection).notificationsFor(userId).willReturn(notifications)
    }

    api.notificationsFor(sessionId) must beEqualTo(UserNotifications(notifications, unread = 1))
  }

  "approve friend request" in new LoggedInContext {
    checking {
      oneOf(commandProcessor).process(ChangeFriendRequestStatus(friendRequestId, Approved), userId)
    }

    api.approveFriendRequest(sessionId, friendRequestId)
  }

  "ignore friend request" in new LoggedInContext {
    checking {
      oneOf(commandProcessor).process(ChangeFriendRequestStatus(friendRequestId, Ignored), userId)
    }

    api.ignoreFriendRequest(sessionId, friendRequestId)
  }

  "return friend profile" in new LoggedInContext {
    val friendName = "Joe"

    checking {
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId).withFriend(friendId, friendRequestId).list)
      allowing(dataStore).userEvents(friendId).willReturn(EventsList(friendId).withName(friendName).list)
    }

    api.userProfileFor(sessionId, friendId) must beRight(UserProfile(name = Some(friendName)))
  }

  "return minimal profile when not friends" in new LoggedInContext {
    val strangerFirstName = "Martin"
    val strangerName = strangerFirstName + " Strange"
    val strangerImage = "expectedImageLink"

    checking {
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId).list)
      allowing(dataStore).userEvents(friendId).willReturn(EventsList(friendId).withName(strangerName).withPic(strangerImage).list)
    }
    api.userProfileFor(sessionId, friendId) must beRight(UserProfile(name = Option(strangerName), picture = Option(strangerImage)))
  }

  "return friend wishlist" in new LoggedInContext {
    val friendName = "Joe"
    val wishId = randomUUID()
    val wishName = "expected wish"

    checking {
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId).withFriend(friendId, friendRequestId).list)
      allowing(dataStore).userEvents(friendId).willReturn(EventsList(friendId).withName(friendName).withWish(wishId, wishName).list)
    }

    api.wishListFor(sessionId, friendId) must beRight(userWishesWith(wishId, wishName))
  }

  "return error when not friends" in new LoggedInContext {
    checking {
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId).list)
    }
    api.wishListFor(sessionId, friendId) must beLeft[ValidationError](NotFriends)
  }

  "return friend friends" in new LoggedInContext {
    val otherFriend = Friend(randomUUID())
    val friends = UserFriends(List(Friend(userId), otherFriend))
    checking {
      allowing(dataStore).userEvents(userId).willReturn(EventsList(userId).withFriend(friendId, friendRequestId).list)
      allowing(userFriendsProjection).friendsFor(friendId, userId).willReturn(friends)
    }

    api.friendsListFor(sessionId, friendId) must beRight(UserFriends(List(otherFriend)))
  }

  "unfriend" in new LoggedInContext {
    checking {
      oneOf(commandProcessor).process(RemoveFriend(friendId), userId)
    }
    api.unfriend(sessionId, friendId)
  }

  "grant wish" in new LoggedInContext {
    val wishId = randomUUID()
    checking {
      oneOf(commandProcessor).process(GrantWish(wishId), userId)
    }

    api.grantWish(userId, wishId)
  }

  def userWishesWith(wishId: UUID, wishName: String): Matcher[UserWishes] = contain(aWishWith(wishId, wishName)) ^^ {(_: UserWishes).wishes}

  def aWishWith(id: UUID, name: String): Matcher[Wish] = (wish: Wish) =>
    (wish.id == id && wish.name.isDefined && wish.name.get == name, s"Wish $wish does not match name $name and id $id")


  trait Context extends Scope {
    val sessionId: UUID = randomUUID()
    val userId: UUID = randomUUID()
    val dataStore: DataStore = mock[DataStore]
    val notificationsProjection = mock[NotificationsProjection]
    val commandProcessor = mock[CommandProcessor]
    val userFriendsProjection: UserFriendsProjection = mock[UserFriendsProjection]
    val userProfileProjection: UserProfileProjection = new ReplayingUserProfileProjection(dataStore)
    val api: PublicApi = new DelegatingPublicApi(commandProcessor,
      dataStore, null, null, userProfileProjection, userFriendsProjection, notificationsProjection, null)(null, null, null)
    val friendId: UUID = randomUUID()
    val friendRequestId = randomUUID()
    val notificationData = FriendRequestNotification(friendId, friendRequestId)
  }

  trait LoggedInContext extends Context {
    checking {
      allowing(dataStore).userBySession(sessionId).willReturn(Option(userId))
    }
  }

  def aNotificationWith(data: NotificationData, viewed: Boolean): Matcher[Notification] = (notification: Notification) =>
    (notification.data == data && viewed == notification.viewed,
      s"$notification does not match $data and viewed[$viewed]")
}
