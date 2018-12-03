package co.wishkeeper.server.user.events

import co.wishkeeper.server.Events._
import co.wishkeeper.server.User
import org.joda.time.DateTime

object FlagsHandlers {
  implicit val facebookHandler = new UserEventHandler[FacebookFriendsListSeen] {
    override def apply(user: User, event: FacebookFriendsListSeen, time: DateTime): User =
      user.copy(flags = user.flags.copy(seenFacebookFriendsList = event.seen))
  }

  implicit val googleHandler = new UserEventHandler[GoogleFriendsListSeen] {
    override def apply(user: User, event: GoogleFriendsListSeen, time: DateTime): User =
      user.copy(flags = user.flags.copy(seenGoogleFriendsList = event.seen))
  }

  implicit val openEmailConnectHandler = new UserEventHandler[EmailConnectStarted] {
    override def apply(user: User, event: EmailConnectStarted, time: DateTime): User =
      user.copy(flags = user.flags.copy(haveOpenEmailConnect = true))
  }

  implicit val emailVerifiedHandler = new UserEventHandler[EmailVerified] {
    override def apply(user: User, event: EmailVerified, time: DateTime): User =
      user.copy(flags = user.flags.copy(haveOpenEmailConnect = false, emailVerified = true))
  }

  implicit val connectedHandler = new UserEventHandler[UserConnected] {
    override def apply(user: User, event: UserConnected, time: DateTime): User =
      user.copy(flags = user.flags.copy(everConnected = true))
  }
}
