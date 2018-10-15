package co.wishkeeper.server.user.events

import co.wishkeeper.server.Events.{FacebookFriendsListSeen, GoogleFriendsListSeen}
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
}
