package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.Events._

case class User(id: UUID, userProfile: UserProfile = UserProfile(), friends: Friends = Friends()) {

  def applyEvent(event: UserEvent): User = event match {
    case UserFirstNameSet(_, value) => this.copy(userProfile = this.userProfile.copy(firstName = Option(value)))
    case UserLastNameSet(_, value) => this.copy(userProfile = this.userProfile.copy(lastName = Option(value)))
    case UserNameSet(_, value) => this.copy(userProfile = this.userProfile.copy(name = Option(value)))
    case UserBirthdaySet(_, value) => this.copy(userProfile = this.userProfile.copy(birthday = Option(value)))
    case UserEmailSet(_, value) => this.copy(userProfile = this.userProfile.copy(email = Option(value)))
    case UserLocaleSet(_, value) => this.copy(userProfile = this.userProfile.copy(locale = Option(value)))
    case UserGenderSet(_, value) => this.copy(userProfile = this.userProfile.copy(gender = Option(value)))
    case UserTimeZoneSet(_, value) => this.copy(userProfile = this.userProfile.copy(timezone = Option(value)))
    case UserAgeRangeSet(_, min, max) => this.copy(userProfile = this.userProfile.copy(ageRange = Option(AgeRange(min, max))))
    case UserFacebookIdSet(_, fbId) => this.copy(userProfile = this.userProfile.copy(socialData =
      this.userProfile.socialData match {
        case Some(data) => Option(data.copy(facebookId = Option(fbId)))
        case None => Option(SocialData(Option(fbId)))
      }))
    case FriendRequestSent(_, friendId) => this.copy(friends = this.friends.copy(pending = this.friends.pending :+ friendId))
    case FriendRequestReceived(_, friendId) => this.copy(friends = this.friends.copy(awaitingApproval = this.friends.awaitingApproval :+ friendId))
    case _ => this
  }

  lazy val facebookId: Option[String] = userProfile.socialData.flatMap(_.facebookId)
}

object User {
  def replay[T <: UserEvent](events: List[T]): User = {
    events.headOption.map {
      case UserConnected(userId, _, _) =>
        events.foldLeft(User(userId))(_.applyEvent(_))
    }.getOrElse(throw new IllegalArgumentException("Event stream does not begin with UserConnected"))
  }

  def createNew() = new User(UUID.randomUUID())
}

case class Friends(current: List[UUID] = Nil, pending: List[UUID] = Nil, awaitingApproval: List[UUID] = Nil)