package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.Events._

case class User(id: UUID,
                userProfile: UserProfile = UserProfile(),
                friends: Friends = Friends(),
                wishes: Map[UUID, Wish] = Map.empty,
                flags: Flags = Flags(),
                notifications: List[Notification] = Nil) {

  def applyEvent(event: UserEventInstant): User = event match {
    case UserEventInstant(UserFirstNameSet(_, value), _) => this.copy(userProfile = this.userProfile.copy(firstName = Option(value)))
    case UserEventInstant(UserLastNameSet(_, value), _) => this.copy(userProfile = this.userProfile.copy(lastName = Option(value)))
    case UserEventInstant(UserNameSet(_, value), _) => this.copy(userProfile = this.userProfile.copy(name = Option(value)))
    case UserEventInstant(UserBirthdaySet(_, value), _) => this.copy(userProfile = this.userProfile.copy(birthday = Option(value)))
    case UserEventInstant(UserEmailSet(_, value), _) => this.copy(userProfile = this.userProfile.copy(email = Option(value)))
    case UserEventInstant(UserLocaleSet(_, value), _) => this.copy(userProfile = this.userProfile.copy(locale = Option(value)))
    case UserEventInstant(UserGenderSet(_, value), _) => this.copy(userProfile = this.userProfile.copy(gender = Option(value)))
    case UserEventInstant(UserTimeZoneSet(_, value), _) => this.copy(userProfile = this.userProfile.copy(timezone = Option(value)))
    case UserEventInstant(UserAgeRangeSet(_, min, max), _) => this.copy(userProfile = this.userProfile.copy(ageRange = Option(AgeRange(min, max))))
    case UserEventInstant(UserFacebookIdSet(_, fbId), _) => this.copy(userProfile = this.userProfile.copy(socialData =
      this.userProfile.socialData match {
        case Some(data) => Option(data.copy(facebookId = Option(fbId)))
        case None => Option(SocialData(Option(fbId)))
      }))
    case UserEventInstant(UserPictureSet(_, link), _) => this.copy(userProfile = userProfile.copy(picture = Option(link)))
    case UserEventInstant(FriendRequestSent(_, friendId), _) => this.copy(
      friends = this.friends.copy(requestSent = this.friends.requestSent :+ friendId))
    case UserEventInstant(FriendRequestReceived(_, friendId), _) => this.copy(
      friends = this.friends.copy(requestReceived = this.friends.requestReceived :+ friendId))
    case UserEventInstant(WishCreated(wishId, creator, creationTime), _) => updateWishProperty(wishId, _.withCreationTime(creationTime).withCreator(creator))
    case UserEventInstant(WishNameSet(wishId, name), _) => updateWishProperty(wishId, _.withName(name))
    case UserEventInstant(WishLinkSet(wishId, link), _) => updateWishProperty(wishId, _.withLink(link))
    case UserEventInstant(WishPriceSet(wishId, price), _) => updateWishProperty(wishId, _.withPrice(price))
    case UserEventInstant(WishCurrencySet(wishId, currency), _) => updateWishProperty(wishId, _.withCurrency(currency))
    case UserEventInstant(WishStoreSet(wishId, store), _) => updateWishProperty(wishId, _.withStore(store))
    case UserEventInstant(WishOtherInfoSet(wishId, info), _) => updateWishProperty(wishId, _.withOtherInfo(info))
    case UserEventInstant(WishImageSet(wishId, imageLinks), _) => updateWishProperty(wishId, _.withImage(imageLinks))
    case UserEventInstant(WishImageDeleted(wishId), _) => updateWishProperty(wishId, _.withoutImage)
    case UserEventInstant(WishDeleted(wishId), _) => updateWishProperty(wishId, _.withStatus(WishStatus.Deleted))
    case UserEventInstant(FacebookFriendsListSeen(seen), _) => this.copy(flags = flags.copy(seenFacebookFriendsList = seen))
    case UserEventInstant(FriendRequestNotificationCreated(notificationId, _, from), _) => this.copy(
      notifications = Notification(notificationId, FriendRequestNotification(from)) :: notifications)
    case _ => this
  }

  def seenFacebookFriends: Boolean = flags.seenFacebookFriendsList

  private def updateWishProperty(wishId: UUID, updater: Wish => Wish) =
    this.copy(wishes = wishes + (wishId -> updater(wishes.getOrElse(wishId, Wish(wishId)))))

  lazy val facebookId: Option[String] = userProfile.socialData.flatMap(_.facebookId)
}

object User {
  def replay(events: List[UserEventInstant]): User = {
    events.headOption.map {
      case UserEventInstant(UserConnected(userId, _, _), _) =>
        events.foldLeft(User(userId))((user, instant) => user.applyEvent(instant))
    }.getOrElse(throw new IllegalArgumentException("Event stream does not begin with UserConnected"))
  }

  def createNew() = new User(UUID.randomUUID())
}

case class Friends(current: List[UUID] = Nil, requestSent: List[UUID] = Nil, requestReceived: List[UUID] = Nil)

case class Flags(seenFacebookFriendsList: Boolean = false)