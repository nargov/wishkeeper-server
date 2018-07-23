package co.wishkeeper.server.projections

import java.util.UUID

import co.wishkeeper.server.{DataStore, FacebookConnector, FriendRequest, User}

import scala.concurrent.{ExecutionContext, Future}

trait UserFriendsProjection {

  def potentialFacebookFriends(userId: UUID, accessToken: String): Future[List[PotentialFriend]]

  def friendsFor(userId: UUID): UserFriends

  def friendsFor(friendId: UUID, userId: UUID): UserFriends
}

class EventBasedUserFriendsProjection(facebookConnector: FacebookConnector,
                                      userIdByFacebookId: UserIdByFacebookIdProjection,
                                      dataStore: DataStore)
                                     (implicit ex: ExecutionContext) extends UserFriendsProjection {

  override def potentialFacebookFriends(userId: UUID, accessToken: String): Future[List[PotentialFriend]] = {
    val user = User.replay(dataStore.userEvents(userId))
    val existingOrRequestedFriend: ((String, UUID)) => Boolean = {
      case (_, friendId) =>
        user.hasFriend(friendId) || user.hasPendingFriend(friendId)
    }

    val eventualFacebookFriends = facebookConnector.friendsFor(user.facebookId.get, accessToken)
    eventualFacebookFriends.map { facebookFriends =>
      val facebookIdsToUserIds = userIdByFacebookId.get(facebookFriends.map(_.id))
      facebookIdsToUserIds.filterNot(existingOrRequestedFriend).map {
        case (fbId, id) => PotentialFriend(id, facebookFriends.find(_.id == fbId).get.name, s"https://graph.facebook.com/v2.9/$fbId/picture") //TODO replace image
      }.toList
    }
  }

  override def friendsFor(userId: UUID): UserFriends = {
    val user = User.replay(dataStore.userEvents(userId))
    val friends = user.friends.current.map(friendDetails).sorted(Friend.ordering)
    val requested = user.friends.sentRequests.map(userIdFromFriendRequest andThen friendDetails)
    val incoming = user.friends.receivedRequests.map(req => IncomingFriendRequest(req.id, friendDetails(req.from)))
    UserFriends(friends, requested = requested, incoming = incoming)
  }

  private val userIdFromFriendRequest: FriendRequest => UUID = _.userId

  private val friendDetails: UUID => Friend = friendId => {
    val friend = User.replay(dataStore.userEvents(friendId))
    Friend(friendId, friend.userProfile.name, friend.userProfile.picture, friend.userProfile.firstName)
  }

  override def friendsFor(friendId: UUID, userId: UUID) = {
    val userFriends = User.replay(dataStore.userEvents(userId)).friends
    val userCurrentFriends = userFriends.current.toSet
    val sentFriendRequests = userFriends.sentRequests.toSet
    val friendFriends = friendsFor(friendId)
    val (mutualFriends, friends) = friendFriends.list.partition(friend => userCurrentFriends.contains(friend.userId))
    val (potentialMutual, onlyFriendFriends) = friends.partition(friend => sentFriendRequests.exists(_.userId == friend.userId))
    if (userFriends.current.contains(friendId))
      UserFriends(onlyFriendFriends, mutualFriends, potentialMutual)
    else
      UserFriends(Nil, mutualFriends, Nil)
  }
}


case class Friend(userId: UUID, name: Option[String] = None, image: Option[String] = None, firstName: Option[String] = None)
object Friend {
  implicit val ordering = new Ordering[Friend]{
    override def compare(x: Friend, y: Friend): Int = (x.name, y.name) match {
      case (Some(xName), Some(yName)) => xName.compareToIgnoreCase(yName)
      case (Some(_), None) => -1
      case (None, Some(_)) => 1
      case (None, None) => 0
    }
  }
}

case class IncomingFriendRequest(id: UUID, friend: Friend)

case class UserFriends(list: List[Friend], mutual: List[Friend] = Nil, requested: List[Friend] = Nil, incoming: List[IncomingFriendRequest] = Nil) {
  def excluding(friendId: UUID) = copy(list = list.filterNot(_.userId == friendId))
}