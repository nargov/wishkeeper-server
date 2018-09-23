package co.wishkeeper.server.user.commands

import java.util.UUID

import co.wishkeeper.server.Events._
import co.wishkeeper.server.user._
import co.wishkeeper.server.{FriendRequestStatus, GeneralSettings, User}
import org.joda.time.LocalDate

trait UserCommand {
  def process(user: User): List[UserEvent]
}

case class SendFriendRequest(friendId: UUID) extends UserCommand {
  override def process(user: User): List[UserEvent] =
    if (user.friends.sentRequests.exists(_.userId == friendId))
      Nil
    else
      List(FriendRequestSent(user.id, friendId, Option(UUID.randomUUID())))
}

object SendFriendRequest {
  implicit val validator = new UserCommandValidator[SendFriendRequest] {
    override def validate(user: User, command: SendFriendRequest): Either[ValidationError, Unit] = {
      if (user.hasFriend(command.friendId)) Left(AlreadyFriend(command.friendId))
      else Right(())
    }
  }
}


case class SetFlagFacebookFriendsListSeen(seen: Boolean = true) extends UserCommand {
  override def process(user: User): List[UserEvent] = FacebookFriendsListSeen(seen) :: Nil
}

case class ChangeFriendRequestStatus(requestId: UUID, status: FriendRequestStatus) extends UserCommand {
  override def process(user: User): List[UserEvent] = {
    user.friends.receivedRequests.
      find(_.id == requestId).
      map(request => FriendRequestStatusChanged(user.id, requestId, request.from, status) :: Nil).
      getOrElse(Nil)
  }
}

object ChangeFriendRequestStatus {
  implicit val validator: UserCommandValidator[ChangeFriendRequestStatus] = UserCommandValidator.Always
}

case object MarkAllNotificationsViewed extends UserCommand {
  override def process(user: User): List[UserEvent] =
    user.notifications.filterNot(_.viewed).map(notification => NotificationViewed(notification.id))
}

case class MarkNotificationViewed(id: UUID) extends UserCommand {
  override def process(user: User): List[UserEvent] = NotificationViewed(id) :: Nil
}

object MarkNotificationViewed {
  implicit val validator: UserCommandValidator[MarkNotificationViewed] = (user, event) => Either.cond(
    user.notifications.exists(n => n.id == event.id && !n.viewed), (), AlreadyViewed(event.id))
}

case class RemoveFriend(friendId: UUID) extends UserCommand {
  override def process(user: User): List[UserEvent] = FriendRemoved(user.id, friendId) :: Nil
}

object RemoveFriend {
  implicit val validator: UserCommandValidator[RemoveFriend] = (user, event) => Either.cond(user.hasFriend(event.friendId), (), NotFriends)
}

case class SetDeviceNotificationId(id: String) extends UserCommand {
  override def process(user: User): List[UserEvent] = DeviceNotificationIdSet(id) :: Nil
}

object SetDeviceNotificationId {
  implicit val validator = new UserCommandValidator[SetDeviceNotificationId] {
    override def validate(user: User, command: SetDeviceNotificationId): Either[ValidationError, Unit] =
      Either.cond(user.settings.deviceNotificationId != Option(command.id), (), NoChange)
  }
}

case object DeleteUserPicture extends UserCommand {
  override def process(user: User): List[UserEvent] = UserPictureDeleted :: Nil

  implicit val validator: UserCommandValidator[DeleteUserPicture.type] =
    (user: User, _: DeleteUserPicture.type) => Either.cond(user.userProfile.picture.isDefined, (), NoPictureToDelete)
}

case class SetUserName(firstName: Option[String], lastName: Option[String]) extends UserCommand {
  override def process(user: User): List[UserEvent] = List(
    UserFirstNameSet(user.id, firstName.getOrElse("")),
    UserLastNameSet(user.id, lastName.getOrElse("")),
    UserNameSet(user.id, s"${firstName.getOrElse("")} ${lastName.getOrElse("")}".trim)
  )
}

object SetUserName {
  implicit val validator: UserCommandValidator[SetUserName] = UserCommandValidator.Always
}

case class SetUserPicture(url: String) extends UserCommand {
  override def process(user: User): List[UserEvent] = UserPictureSet(user.id, url) :: Nil
}

object SetUserPicture {
  implicit val validator: UserCommandValidator[SetUserPicture] = UserCommandValidator.Always
}

case class SetGender(gender: Gender, customGender: Option[String], genderPronoun: Option[GenderPronoun]) extends UserCommand {
  override def process(user: User): List[UserEvent] = UserGenderSet2(gender, customGender, genderPronoun) :: Nil
}

object SetGender {
  implicit val validator: UserCommandValidator[SetGender] = UserCommandValidator.Always
}

case class SetGeneralSettings(generalSettings: GeneralSettings) extends UserCommand {
  override def process(user: User): List[UserEvent] = {
    val settings = user.settings.general
    val pushEnabled: List[UserEvent] = if (settings.pushNotificationsEnabled != generalSettings.pushNotificationsEnabled)
      GeneralSettingPushNotificationEnabledSet(generalSettings.pushNotificationsEnabled) :: Nil
    else Nil

    val vibrateEnabled: List[UserEvent] = if (settings.vibrate != generalSettings.vibrate)
      GeneralSettingVibrateEnabledSet(generalSettings.vibrate) :: Nil
      else Nil

    pushEnabled ++ vibrateEnabled
  }
}
object SetGeneralSettings {
  implicit val validator: UserCommandValidator[SetGeneralSettings] = UserCommandValidator.Always
}

case class SetUserBirthday(birthday: LocalDate) extends UserCommand {
  override def process(user: User): List[UserEvent] = UserBirthdaySet(user.id, birthday.toString("MM/dd/yyyy")) :: Nil
}
object SetUserBirthday {
  implicit val validator: UserCommandValidator[SetUserBirthday] = UserCommandValidator.Always
}

case class SetAnniversary(anniversary: LocalDate) extends UserCommand {
  override def process(user: User): List[UserEvent] = UserAnniversarySet(anniversary.toString("MM/dd/yyyy")) :: Nil
}
object SetAnniversary {
  implicit val validator: UserCommandValidator[SetAnniversary] = UserCommandValidator.Always
}