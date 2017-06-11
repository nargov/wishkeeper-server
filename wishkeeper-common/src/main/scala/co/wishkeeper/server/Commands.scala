package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.Events._
import org.joda.time.DateTime

object Commands {

  sealed trait UserCommand {
    def process(user: User): List[UserEvent]
  }

  case class ConnectFacebookUser(facebookId: String, authToken: String, sessionId: UUID) extends UserCommand {
    override def process(user: User): List[UserEvent] = {
      val now = DateTime.now()
      val userConnectedEvent = UserConnected(user.id, now, sessionId)
      val facebookIdSetEvent = UserFacebookIdSet(user.id, facebookId) //TODO only do this if facebook id was never set
      List(userConnectedEvent, facebookIdSetEvent)
    }
  }

  case class SetFacebookUserInfo(age_range: Option[AgeRange] = None,
                                 birthday: Option[String] = None,
                                 email: Option[String] = None,
                                 first_name: Option[String] = None,
                                 last_name: Option[String] = None,
                                 name: Option[String] = None,
                                 gender: Option[String] = None,
                                 locale: Option[String] = None,
                                 timezone: Option[Int] = None) extends UserCommand {

    override def process(user: User): List[UserEvent] = {
      val userId = user.id
      List(
        age_range.map(range => UserAgeRangeSet(userId, range.min, range.max)),
        birthday.flatMap(SetFacebookUserInfo.getValidUserBirthdayEvent(userId, _)),
        email.map(UserEmailSet(userId, _)),
        gender.map(UserGenderSet(userId, _)),
        locale.map(UserLocaleSet(userId, _)),
        timezone.map(UserTimeZoneSet(userId, _)),
        first_name.map(UserFirstNameSet(userId, _)),
        last_name.map(UserLastNameSet(userId, _)),
        name.map(UserNameSet(userId, _))).flatten
    }
  }

  object SetFacebookUserInfo {
    val getValidUserBirthdayEvent: (UUID, String) => Option[UserBirthdaySet] = (userId, day) => {
      if (day.matches("""^\d{2}/\d{2}(/\d{4})?$"""))
        Some(UserBirthdaySet(userId, day))
      else None
    }
  }

  case class SendFriendRequest(friendId: UUID) extends UserCommand {
    override def process(user: User): List[UserEvent] = List(FriendRequestSent(user.id, friendId))
  }

  case class SetWishDetails(wish: Wish) extends UserCommand {
    override def process(user: User): List[UserEvent] = List(
      wish.name.map(WishNameSet(wish.id, _)),
      wish.link.map(WishLinkSet(wish.id, _)),
      wish.imageLink.map(WishImageLinkSet(wish.id, _)),
      wish.store.map(WishStoreSet(wish.id, _)),
      wish.otherInfo.map(WishOtherInfoSet(wish.id, _)),
      wish.price.map(WishPriceSet(wish.id, _)),
      wish.currency.map(WishCurrencySet(wish.id, _))
    ).flatten
  }

  case class DeleteWishImage(wishId: UUID) extends UserCommand {
    override def process(user: User): List[UserEvent] = List(WishImageDeleted(wishId))
  }

  case class CreateNewWish(wishId: UUID) extends UserCommand {
    override def process(user: User): List[UserEvent] = List(WishCreated(wishId, user.id))
  }
}