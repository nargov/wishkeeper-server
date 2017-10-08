package co.wishkeeper.server.projections

import java.util.UUID

import co.wishkeeper.server.{DataStore, FacebookConnector, User}

import scala.concurrent.{ExecutionContext, Future}

trait UserFriendsProjection {
  def potentialFacebookFriends(facebookId: String, accessToken: String): Future[List[PotentialFriend]]
  def friendsFor(userId: UUID): UserFriends
}

class SimpleUserFriendsProjection(facebookConnector: FacebookConnector,
                                  userIdByFacebookId: UserIdByFacebookIdProjection,
                                  dataStore: DataStore)
                                 (implicit ex: ExecutionContext) extends UserFriendsProjection {


  override def potentialFacebookFriends(facebookId: String, accessToken: String): Future[List[PotentialFriend]] = {
    val eventualFriends = facebookConnector.friendsFor(facebookId, accessToken)
    eventualFriends.map { friends =>
      val facebookIdsToUserIds = userIdByFacebookId.get(friends.map(_.id))
      facebookIdsToUserIds.map {
        case (fbId, userId) => PotentialFriend(userId, friends.find(_.id == fbId).get.name, s"https://graph.facebook.com/v2.9/$fbId/picture")
      }.toList
    }
  }

  override def friendsFor(userId: UUID): UserFriends = {
    UserFriends(User.replay(dataStore.userEvents(userId)).friends.current.map { friendId =>
      val friend = User.replay(dataStore.userEvents(friendId))
      Friend(friendId, friend.userProfile.name, friend.userProfile.picture)
    })
  }
}

case class PotentialFriend(userId: UUID, name: String, image: String, requestSent: Boolean = false)
case class Friend(userId: UUID, name: Option[String], image: Option[String])
case class UserFriends(list: List[Friend])