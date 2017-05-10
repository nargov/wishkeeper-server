package co.wishkeeper.server.projections

import co.wishkeeper.server.{FacebookConnector, PotentialFriend}

import scala.concurrent.{ExecutionContext, Future}

trait UserFriendsProjection {
  def potentialFacebookFriends(facebookId: String, accessToken: String): Future[List[PotentialFriend]]
}

class DelegatingUserFriendsProjection(facebookConnector: FacebookConnector, userIdByFacebookId: UserIdByFacebookIdProjection)
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
}