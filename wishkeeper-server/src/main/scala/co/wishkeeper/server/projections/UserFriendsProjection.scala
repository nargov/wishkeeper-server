package co.wishkeeper.server.projections

import java.util.UUID

import co.wishkeeper.server.WishStatus.Active
import co.wishkeeper.server.projections.UserRelation.{DirectFriend, IncomingRequest, RequestedFriend}
import co.wishkeeper.server.{DataStore, Error, FacebookConnector, FriendRequest, GeneralError, GoogleAuthAdapter, User, Wish}
import org.joda.time.{DateTime, LocalDate}
import org.joda.time.format.DateTimeFormat

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait UserFriendsProjection {

  def friendsWithUpcomingBirthday(userId: UUID, birthdayMarginDays: Int = 14, maxWishes: Int = 7,
                                  today: LocalDate = new LocalDate()): Either[Error, UpcomingBirthdayFriends]

  def friendsBornToday(userId: UUID, date: DateTime = DateTime.now()): Either[Error, FriendBirthdaysResult]

  def potentialFacebookFriends(userId: UUID, accessToken: String): Future[List[PotentialFriend]]

  def potentialGoogleFriends(userId: UUID, accessToken: String): Either[Error, PotentialFriends]

  def friendsFor(userId: UUID): UserFriends

  def friendsFor(friendId: UUID, userId: UUID): UserFriends
}

class EventBasedUserFriendsProjection(facebookConnector: FacebookConnector,
                                      googleAuth: GoogleAuthAdapter,
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
        case (fbId, id) => PotentialFriend(id, facebookFriends.find(_.id == fbId).get.name, s"https://graph.facebook.com/v2.9/$fbId/picture")
      }.toList
    }
  }

  override def friendsFor(userId: UUID): UserFriends = {
    val user = User.replay(dataStore.userEvents(userId))
    val friends = user.friends.current.map(friendDetails).sorted(Friend.ordering)
    val requested = user.friends.sentRequests.map(userIdFromFriendRequest andThen friendDetails)
    val incoming = user.friends.receivedRequests.map(req => IncomingFriendRequest(req.id, friendDetails(req.from)))
    UserFriends(friends, requested = requested, incoming = incoming, all = friends.map(_.asDirectFriend))
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
    val marked = (onlyFriendFriends.filterNot(_.userId == userId) ++
      mutualFriends.map(_.asDirectFriend) ++
      potentialMutual.map(_.asRequestedFriend)).sorted(Friend.ordering)

    if (userFriends.current.contains(friendId))
      UserFriends(onlyFriendFriends, mutualFriends, potentialMutual, all = marked)
    else
      UserFriends(Nil, mutualFriends, Nil, all = mutualFriends.map(_.asDirectFriend))
  }

  override def friendsBornToday(userId: UUID, date: DateTime = DateTime.now()): Either[Error, FriendBirthdaysResult] = {
    val friendIds: List[UUID] = User.replay(dataStore.userEvents(userId)).friends.current
    val friends = friendIds.map(friendId => User.replay(dataStore.userEvents(friendId)))
    val bornToday = friends.filter(_.userProfile.birthday.exists(dateStr => {
      val birthDate = DateTime.parse(dateStr, DateTimeFormat.shortDate())
      birthDate.monthOfYear() == date.monthOfYear() && birthDate.dayOfMonth() == date.dayOfMonth()
    }))
    Right(FriendBirthdaysResult(bornToday.map(friend =>
      Friend(friend.id, friend.userProfile.name, friend.userProfile.picture, friend.userProfile.firstName, Option(DirectFriend)))))
  }

  override def potentialGoogleFriends(userId: UUID, accessToken: String): Either[Error, PotentialFriends] =
    googleAuth.userContactEmails(accessToken).flatMap { emails =>
      Try {
        val emailsSet = emails.toSet
        val user = User.replay(dataStore.userEvents(userId))
        val userFilter: ((String, UUID)) => Boolean = entry =>
          emailsSet.contains(entry._1) && !user.friends.current.contains(entry._2)
        PotentialFriends(dataStore.userEmails.filter(userFilter).map { case (_, id) =>
          val user = User.replay(dataStore.userEvents(id))
          PotentialFriend(user.id, user.userProfile.name.getOrElse(""), user.userProfile.picture.getOrElse(""))
        }.toList)
      }.toEither.left.map[Error] { t =>
        GeneralError(t.getMessage)
      }
    }

  override def friendsWithUpcomingBirthday(userId: UUID, birthdayMarginDays: Int = 14, maxWishes: Int = 7,
                                           today: LocalDate = new LocalDate()): Either[Error, UpcomingBirthdayFriends] = {
    val user = User.replay(dataStore.userEvents(userId))
    val friends = user.friends.current.flatMap(friendId => {
      val friend = User.replay(dataStore.userEvents(friendId))
      if (isRelevantFriend(userId, birthdayMarginDays, friend, today)) {
        val friendProfile = friend.userProfile
        val activeWishesByDate = friend.activeWishesByDate
        Some(FriendWishlistPreview(Friend(friendId, friendProfile.name, friendProfile.picture, friendProfile.firstName, Option(DirectFriend)),
          friendProfile.birthday, activeWishesByDate.take(maxWishes), activeWishesByDate.size > maxWishes))
      }
      else {
        None
      }
    })
    Right(UpcomingBirthdayFriends(friends))
  }

  private val isRelevantFriend: (UUID, Int, User, LocalDate) => Boolean = (userId, birthdayMarginDays, f, today) => {
    val wishlist = f.wishes.values
    wishlist.exists(_.status == Active) &&
      !wishlist.exists(_.reserver.contains(userId)) &&
      f.userProfile.birthday.exists(d => {
        val birthDate = DateTimeFormat.shortDate().parseLocalDate(d).withYear(today.getYear)
        val nextBirthday = if(birthDate.isBefore(today)) birthDate.plusYears(1) else birthDate
        nextBirthday.minusDays(birthdayMarginDays).isBefore(today)
      })
  }
}

case class FriendWishlistPreview(friend: Friend, birthday: Option[String], wishlist: List[Wish], hasMoreWishes: Boolean = false)

case class UpcomingBirthdayFriends(friends: List[FriendWishlistPreview] = List.empty)

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

case class FriendBirthdaysResult(friends: List[Friend])

sealed trait UserRelation

object UserRelation {

  case object DirectFriend extends UserRelation

  case object RequestedFriend extends UserRelation

  case class IncomingRequest(id: UUID) extends UserRelation

}

case class IncomingFriendRequest(id: UUID, friend: Friend)

case class UserFriends(list: List[Friend], mutual: List[Friend] = Nil, requested: List[Friend] = Nil, incoming: List[IncomingFriendRequest] = Nil,
                       all: List[Friend] = Nil) {
  def excluding(friendId: UUID) = copy(list = list.filterNot(_.userId == friendId))
}