package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.Events._

case class User(id: UUID, userProfile: UserProfile = UserProfile(), friends: Friends = Friends(), wishes: Map[UUID, Wish] = Map.empty) {

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
    case UserPictureSet(_, link) => this.copy(userProfile = userProfile.copy(picture = Option(link)))
    case FriendRequestSent(_, friendId) => this.copy(friends = this.friends.copy(requestSent = this.friends.requestSent :+ friendId))
    case FriendRequestReceived(_, friendId) => this.copy(friends = this.friends.copy(requestReceived = this.friends.requestReceived :+ friendId))
    case WishCreated(wishId, creator, creationTime) => updateWishProperty(wishId, _.withCreationTime(creationTime).withCreator(creator))
    case WishNameSet(wishId, name) => updateWishProperty(wishId, _.withName(name))
    case WishLinkSet(wishId, link) => updateWishProperty(wishId, _.withLink(link))
    case WishPriceSet(wishId, price) => updateWishProperty(wishId, _.withPrice(price))
    case WishCurrencySet(wishId, currency) => updateWishProperty(wishId, _.withCurrency(currency))
    case WishStoreSet(wishId, store) => updateWishProperty(wishId, _.withStore(store))
    case WishOtherInfoSet(wishId, info) => updateWishProperty(wishId, _.withOtherInfo(info))
    case WishImageSet(wishId, imageLinks) => updateWishProperty(wishId, _.withImage(imageLinks))
    case WishImageDeleted(wishId) => updateWishProperty(wishId, _.withoutImage)
    case _ => this
  }

  private def updateWishProperty(wishId: UUID, updater: Wish => Wish) =
    this.copy(wishes = wishes + (wishId -> updater(wishes.getOrElse(wishId, Wish(wishId)))))

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

case class Friends(current: List[UUID] = Nil, requestSent: List[UUID] = Nil, requestReceived: List[UUID] = Nil)