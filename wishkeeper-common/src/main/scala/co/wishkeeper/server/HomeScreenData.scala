package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.UserRelation.{DirectFriend, IncomingRequest, RequestedFriend}

case class HomeScreenData(birthdays: FriendsWishlistPreviews = FriendsWishlistPreviews(),
                          updatedWishlists: FriendsWishlistPreviews = FriendsWishlistPreviews())

case class FriendWishlistPreview(friend: Friend, birthday: Option[String], wishlist: List[Wish], hasMoreWishes: Boolean = false,
                                 gender: Option[GenderData] = None)

case class FriendsWishlistPreviews(friends: List[FriendWishlistPreview] = List.empty)

case class Friend(userId: UUID, name: Option[String] = None, image: Option[String] = None, firstName: Option[String] = None,
                  relation: Option[UserRelation] = None) {
  def asDirectFriend = copy(relation = Option(DirectFriend))

  def asRequestedFriend = copy(relation = Option(RequestedFriend))

  def asIncomingRequestFriend(id: UUID) = copy(relation = Option(IncomingRequest(id)))

  def named(name: String) = copy(name = Option(name))
}

object Friend {
  implicit val ordering = new Ordering[Friend] {
    override def compare(x: Friend, y: Friend): Int = (x.name, y.name) match {
      case (Some(xName), Some(yName)) => xName.compareToIgnoreCase(yName)
      case (Some(_), None) => -1
      case (None, Some(_)) => 1
      case (None, None) => 0
    }
  }
}

sealed trait UserRelation

object UserRelation {

  case object DirectFriend extends UserRelation

  case object RequestedFriend extends UserRelation

  case class IncomingRequest(id: UUID) extends UserRelation

}
