package co.wishkeeper.server.api

import java.io.InputStream
import java.net.{HttpURLConnection, URL}
import java.nio.file.{Files, Path, Paths}
import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import cats.data.EitherT
import cats.implicits._
import co.wishkeeper.server.FriendRequestStatus.{Approved, Ignored}
import co.wishkeeper.server.WishStatus.{Active, Reserved, WishStatus}
import co.wishkeeper.server._
import co.wishkeeper.server.image._
import co.wishkeeper.server.messaging.EmailSender
import co.wishkeeper.server.projections._
import co.wishkeeper.server.search.{SearchQuery, UserSearchProjection, UserSearchResults}
import co.wishkeeper.server.user._
import co.wishkeeper.server.user.commands._
import co.wishkeeper.server.user.events.history.HistoryEventInstance
import com.google.common.net.UrlEscapers
import org.joda.time.LocalDate

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait PublicApi {

  def resendVerificationEmail(email: String, idToken: String): Future[Either[Error, Unit]]

  def verifyEmail(verificationToken: UUID): Either[Error, Unit]

  def createUserWithEmail(command: CreateUserEmailFirebase): Future[Either[Error, Unit]]

  def connectFirebaseUser(sessionId: UUID, idToken: String, email: String): Either[Error, Unit]

  def googlePotentialFriends(userId: UUID, accessToken: String): Either[Error, PotentialFriends]

  def setFlagGoogleFriendsSeen(userId: UUID): Either[Error, Unit]

  def setFlagFacebookFriendsSeen(userId: UUID): Either[Error, Unit]

  def connectGoogleUser(command: ConnectGoogleUser): Either[Error, Unit]

  def historyFor(userId: UUID): Either[Error, List[HistoryEventInstance]]

  def historyFor(userId: UUID, friendId: UUID): Either[Error, List[HistoryEventInstance]]

  def setAnniversary(userId: UUID, date: LocalDate): Either[Error, Unit]

  def setBirthday(userId: UUID, date: LocalDate): Either[Error, Unit]

  def getGeneralSettings(userId: UUID): Either[Error, GeneralSettings]

  def setGeneralSettings(userId: UUID, newSettings: GeneralSettings): Either[Error, Unit]

  def setGender(setGender: SetGender, userId: UUID): Either[Error, Unit]

  def uploadProfileImage(inputStream: InputStream, metadata: ImageMetadata, userId: UUID): Either[Error, Unit]

  def setUserName(userId: UUID, setUserName: SetUserName): Either[Error, Unit]

  def friendsBornToday(userId: UUID): Either[Error, FriendBirthdaysResult]

  def removeFriend(userId: UUID, friendId: UUID): Either[Error, Unit]

  def markNotificationAsViewed(userId: UUID, notificationId: UUID): Either[Error, Unit]

  def setNotificationId(userId: UUID, notificationId: String): Either[Error, Unit]

  def sendFriendRequest(userId: UUID, request: SendFriendRequest): Either[Error, Unit]

  def wishById(userId: UUID, wishId: UUID): Either[Error, Wish]

  def wishById(userId: UUID, friendId: UUID, wishId: UUID): Either[Error, Wish]

  def unreserveWish(userId: UUID, friendId: UUID, wishId: UUID): Either[Error, Unit]

  def reserveWish(userId: UUID, friendId: UUID, wishId: UUID): Either[Error, Unit]

  def grantWish(userId: UUID, wishId: UUID, granterId: Option[UUID] = None): Either[Error, Unit]

  def unfriend(sessionId: UUID, friendId: UUID): Either[ValidationError, Unit]

  def markAllNotificationsViewed(sessionId: UUID): Unit

  def friendsListFor(sessionId: UUID, friendId: UUID): Either[ValidationError, UserFriends]

  def friendsListFor(sessionId: UUID): UserFriends

  def ignoreFriendRequest(sessionId: UUID, reqId: UUID): Unit

  def approveFriendRequest(sessionId: UUID, reqId: UUID): Unit

  def notificationsFor(sessionId: UUID): UserNotifications

  def userFlagsFor(sessionId: UUID): Flags

  def deleteWish(userId: UUID, wishId: UUID): Either[Error, Unit]

  def wishListFor(sessionId: UUID): Option[UserWishes]

  def wishListFor(sessionId: UUID, friendId: UUID): Either[ValidationError, UserWishes]

  def processCommand(command: UserCommand, sessionId: Option[UUID]): Unit

  def processCommand[C <: UserCommand](command: C, userId: UUID)(implicit validator: UserCommandValidator[C]): Either[Error, Unit]

  def connectFacebookUser(command: ConnectFacebookUser): Future[Boolean]

  def userProfileFor(sessionId: UUID): Option[UserProfile]

  def userProfileFor(sessionId: UUID, friendId: UUID): Either[ValidationError, UserProfile]

  def facebookPotentialFriends(facebookAccessToken: String, sessionId: UUID): Option[Future[List[PotentialFriend]]]

  def uploadImage(inputStream: InputStream, imageMetadata: ImageMetadata, wishId: UUID, sessionId: UUID): Try[Unit]

  def uploadImage(url: String, imageMetadata: ImageMetadata, wishId: UUID, sessionId: UUID): Try[Unit]

  def deleteWishImage(sessionId: UUID, wishId: UUID): Unit

  def userIdForSession(sessionId: UUID): Option[UUID]

  def searchUser(userId: UUID, query: SearchQuery): Either[Error, UserSearchResults]
}

class DelegatingPublicApi(commandProcessor: CommandProcessor,
                          dataStore: DataStore,
                          facebookConnector: FacebookConnector,
                          userProfileProjection: UserProfileProjection,
                          userFriendsProjection: UserFriendsProjection,
                          notificationsProjection: NotificationsProjection,
                          searchProjection: UserSearchProjection,
                          imageStore: ImageStore,
                          userImageStore: ImageStore,
                          userHistoryProjection: UserHistoryProjection,
                          googleAuth: GoogleAuthAdapter,
                          firebaseAuth: EmailAuthProvider,
                          emailSender: EmailSender)
                         (implicit actorSystem: ActorSystem, ec: ExecutionContext, am: ActorMaterializer) extends PublicApi {

  private val imageUploader = new ImageUploader(imageStore, userImageStore, new ScrimageImageProcessor)

  override def deleteWish(userId: UUID, wishId: UUID): Either[Error, Unit] = commandProcessor.validatedProcess(DeleteWish(wishId), userId)

  override def deleteWishImage(sessionId: UUID, wishId: UUID): Unit =
    withValidSession(sessionId)(commandProcessor.process(DeleteWishImage(wishId), _))

  private def uploadedFilePath(imageMetadata: ImageMetadata): Path = Paths.get(ImageProcessor.tempDir.toString, imageMetadata.fileName)

  override def uploadImage(inputStream: InputStream, imageMetadata: ImageMetadata, wishId: UUID, sessionId: UUID): Try[Unit] = {
    withValidSession(sessionId) { userId =>
      upload(inputStream, imageMetadata).map(imageLinks => {
        val setWishDetailsEvent = SetWishDetails(Wish(wishId, image = Option(imageLinks)))
        commandProcessor.process(setWishDetailsEvent, userId)
      })
    }
  }

  private def upload(inputStream: InputStream, imageMetadata: ImageMetadata, imageStore: Option[ImageStore] = None) = Try {
    val origFile = uploadedFilePath(imageMetadata)
    Files.copy(inputStream, origFile) //TODO move to adapter
    imageStore.fold(imageUploader.uploadImageAndResizedCopies(imageMetadata, origFile))(store =>
      imageUploader.uploadImageAndResizedCopies(imageMetadata, origFile, toImageStore = store))
  }

  val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.113 Safari/537.36"

  private def connectionFor(url: String) = {
    val connection = new URL(url).openConnection().asInstanceOf[HttpURLConnection]
    connection.setRequestProperty("User-Agent", userAgent)
    connection.connect()
    connection
  }

  override def uploadImage(url: String, imageMetadata: ImageMetadata, wishId: UUID, sessionId: UUID): Try[Unit] = {
    Try {
      val escapedUrl = UrlEscapers.urlFragmentEscaper().escape(url)
      val connection = connectionFor(escapedUrl)
      val code = connection.getResponseCode
      if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP || code == HttpURLConnection.HTTP_SEE_OTHER) {
        connectionFor(connection.getHeaderField("Location"))
      }
      else {
        connection
      }
    }.flatMap(con => uploadImage(con.getInputStream, imageMetadata, wishId, sessionId))
  }

  override def wishListFor(sessionId: UUID): Option[UserWishes] = {
    dataStore.userBySession(sessionId).map { userId =>
      UserWishes(activeWishesByDate(replayUser(userId)))
    }
  }

  private val isShownInWishlist: WishStatus => Boolean = {
    case Active | Reserved(_) => true
    case _ => false
  }

  private def activeWishesByDate(user: User) = {
    val wishList = user.wishes.values.toList
    wishList.
      filter(w => isShownInWishlist(w.status)).
      sortBy(_.creationTime.getMillis).
      reverse
  }

  override def wishListFor(sessionId: UUID, friendId: UUID): Either[ValidationError, UserWishes] = withValidSession(sessionId) { userId =>
    val user = replayUser(userId)
    if (user.hasFriend(friendId))
      Right(UserWishes(activeWishesByDate(replayUser(friendId))))
    else
      Left(NotFriends)
  }

  override def processCommand(command: UserCommand, sessionId: Option[UUID]): Unit = commandProcessor.process(command, sessionId)

  override def connectFacebookUser(command: ConnectFacebookUser): Future[Boolean] = {
    val eventualIsValid = facebookConnector.isValid(command.authToken)
    eventualIsValid.map { isValid =>
      if (isValid) commandProcessor.process(command)
      isValid
    }
  }

  override def userProfileFor(sessionId: UUID): Option[UserProfile] = dataStore.userBySession(sessionId).map(userProfileProjection.get)

  override def userProfileFor(sessionId: UUID, friendId: UUID): Either[ValidationError, UserProfile] = {
    withValidSession(sessionId) { userId =>
      val user = User.replay(dataStore.userEvents(userId))
      if (user.hasFriend(friendId))
        Right(userProfileProjection.get(friendId))
      else
        Right(userProfileProjection.strangerProfile(friendId))
    }
  }

  override def facebookPotentialFriends(facebookAccessToken: String, sessionId: UUID): Option[Future[List[PotentialFriend]]] = {
    withValidSession(sessionId) { userId =>
      Option(userFriendsProjection.potentialFacebookFriends(userId, facebookAccessToken))
    }
  }

  override def userFlagsFor(sessionId: UUID): Flags = {
    withValidSession(sessionId) {
      replayUser(_).flags
    }
  }

  private def replayUser(userId: UUID): User = User.replay(dataStore.userEvents(userId))

  override def notificationsFor(sessionId: UUID): UserNotifications = {
    withValidSession(sessionId) { userId =>
      val notifications = notificationsProjection.notificationsFor(userId)
      UserNotifications(notifications, notifications.count(!_.viewed))
    }
  }

  def handleMissingSession(sessionId: UUID) = throw new SessionNotFoundException(Option(sessionId))

  def withValidSession[T](sessionId: UUID)(f: UUID => T): T =
    dataStore.
      userBySession(sessionId).
      map(userId => f(userId)).
      getOrElse(handleMissingSession(sessionId))

  override def approveFriendRequest(sessionId: UUID, reqId: UUID): Unit = {
    withValidSession(sessionId) {
      commandProcessor.process(ChangeFriendRequestStatus(reqId, Approved), _)
    }
  }

  override def ignoreFriendRequest(sessionId: UUID, reqId: UUID): Unit = withValidSession(sessionId) {
    commandProcessor.process(ChangeFriendRequestStatus(reqId, Ignored), _)
  }

  override def friendsListFor(sessionId: UUID): UserFriends = withValidSession(sessionId)(userFriendsProjection.friendsFor)

  override def friendsListFor(sessionId: UUID, friendId: UUID): Either[ValidationError, UserFriends] = withValidSession(sessionId) { userId =>
    val user = User.replay(dataStore.userEvents(userId))
    Right(userFriendsProjection.friendsFor(friendId, user.id).excluding(user.id))
  }

  override def markAllNotificationsViewed(sessionId: UUID): Unit = withValidSession(sessionId) {
    commandProcessor.process(MarkAllNotificationsViewed, _)
  }

  override def unfriend(sessionId: UUID, friendId: UUID): Either[ValidationError, Unit] = withValidSession(sessionId) { userId =>
    commandProcessor.process(RemoveFriend(friendId), userId)
    Right(())
  }

  override def userIdForSession(sessionId: UUID): Option[UUID] = dataStore.userBySession(sessionId)

  override def grantWish(userId: UUID, wishId: UUID, granterId: Option[UUID] = None): Either[Error, Unit] = {
    commandProcessor.validatedProcess(GrantWish(wishId, granterId), userId)
  }

  override def reserveWish(userId: UUID, friendId: UUID, wishId: UUID): Either[Error, Unit] = {
    commandProcessor.validatedProcess(ReserveWish(userId, wishId), friendId)
  }

  //TODO check if user was the original reserver
  override def unreserveWish(userId: UUID, friendId: UUID, wishId: UUID): Either[Error, Unit] = {
    commandProcessor.validatedProcess(UnreserveWish(wishId), friendId)
  }

  override def wishById(userId: UUID, wishId: UUID): Either[Error, Wish] = {
    val wishes = replayUser(userId).wishes
    Either.cond(wishes.contains(wishId), wishes(wishId), WishNotFound(wishId))
  }

  override def processCommand[C <: UserCommand](command: C, userId: UUID)(implicit validator: UserCommandValidator[C]): Either[Error, Unit] =
    commandProcessor.validatedProcess(command, userId)

  override def sendFriendRequest(userId: UUID, request: SendFriendRequest): Either[Error, Unit] = {
    replayUser(userId).friendRequestId(request.friendId).
      fold(commandProcessor.validatedProcess(request, userId))(reqId =>
        commandProcessor.validatedProcess(ChangeFriendRequestStatus(reqId, Approved), userId))
  }

  override def setNotificationId(userId: UUID, notificationId: String): Either[Error, Unit] =
    commandProcessor.validatedProcess(SetDeviceNotificationId(notificationId), userId)

  override def markNotificationAsViewed(userId: UUID, notificationId: UUID): Either[Error, Unit] =
    commandProcessor.validatedProcess(MarkNotificationViewed(notificationId), userId)

  override def removeFriend(userId: UUID, friendId: UUID): Either[Error, Unit] = commandProcessor.validatedProcess(RemoveFriend(friendId), userId)

  override def searchUser(userId: UUID, search: SearchQuery): Either[Error, UserSearchResults] = Right(searchProjection.byName(userId, search.query))

  override def friendsBornToday(userId: UUID): Either[Error, FriendBirthdaysResult] = userFriendsProjection.friendsBornToday(userId)

  override def setUserName(userId: UUID, setUserName: SetUserName): Either[Error, Unit] = commandProcessor.validatedProcess(setUserName, userId)

  override def uploadProfileImage(inputStream: InputStream, metadata: ImageMetadata, userId: UUID): Either[Error, Unit] = {
    Try {
      imageUploader.uploadProfileImage(inputStream, metadata)
    }.toEither.left.map[Error](t => GeneralError(t.getMessage)).flatMap { links =>
      val command = SetUserPicture(links.links.last.url)
      commandProcessor.validatedProcess(command, userId)
    }
  }

  override def setGender(setGender: SetGender, userId: UUID): Either[Error, Unit] = commandProcessor.validatedProcess(setGender, userId)

  override def setGeneralSettings(userId: UUID, newSettings: GeneralSettings): Either[Error, Unit] =
    commandProcessor.validatedProcess(SetGeneralSettings(newSettings), userId)

  override def getGeneralSettings(userId: UUID): Either[Error, GeneralSettings] = Right(User.replay(dataStore.userEvents(userId)).settings.general)

  override def setBirthday(userId: UUID, date: LocalDate): Either[Error, Unit] = commandProcessor.validatedProcess(SetUserBirthday(date), userId)

  override def setAnniversary(userId: UUID, date: LocalDate): Either[Error, Unit] = commandProcessor.validatedProcess(SetAnniversary(date), userId)

  override def historyFor(userId: UUID): Either[Error, List[HistoryEventInstance]] =
    Try(userHistoryProjection.historyFor(userId)).toEither.left.map(t => GeneralError(t.getMessage))

  override def historyFor(userId: UUID, friendId: UUID): Either[Error, List[HistoryEventInstance]] = {
    val user = User.replay(dataStore.userEvents(userId))
    if (user.friends.current.contains(friendId))
      Try(userHistoryProjection.friendHistory(friendId)).toEither.left.map(t => GeneralError(t.getMessage))
    else Left(NotFriends)
  }

  override def wishById(userId: UUID, friendId: UUID, wishId: UUID): Either[Error, Wish] = {
    val user = User.replay(dataStore.userEvents(userId))
    if (user.friends.current.contains(friendId))
      wishById(friendId, wishId)
    else Left(NotFriends)
  }

  override def connectGoogleUser(command: ConnectGoogleUser): Either[Error, Unit] = commandProcessor.connectWithGoogle(command)

  override def setFlagFacebookFriendsSeen(userId: UUID): Either[Error, Unit] =
    commandProcessor.validatedProcess(SetFlagFacebookFriendsListSeen(), userId)

  override def setFlagGoogleFriendsSeen(userId: UUID): Either[Error, Unit] =
    commandProcessor.validatedProcess(SetFlagGoogleFriendsListSeen(), userId)

  override def googlePotentialFriends(userId: UUID, accessToken: String): Either[Error, PotentialFriends] =
    userFriendsProjection.potentialGoogleFriends(userId, accessToken)

  override def connectFirebaseUser(sessionId: UUID, idToken: String, email: String): Either[Error, Unit] =
    commandProcessor.connectWithFirebase(sessionId, idToken, email)

  override def createUserWithEmail(command: CreateUserEmailFirebase): Future[Either[Error, Unit]] =
    commandProcessor.connectWithEmail(command, emailSender).value

  override def verifyEmail(verificationToken: UUID): Either[Error, Unit] = {
    dataStore.verifyEmailToken(verificationToken).flatMap(token => {
      commandProcessor.validatedProcess(MarkEmailVerified, token.userId)
    })
  }

  override def resendVerificationEmail(email: String, idToken: String): Future[Either[Error, Unit]] = {
    val token = UUID.randomUUID()
    dataStore.userIdByEmail(email)
      .fold[Future[Either[Error, Unit]]](Future.successful(Left(UserNotFound(email)))) { userId =>
      val user = User.replay(dataStore.userEvents(userId))
      EitherT(Future(firebaseAuth.validate(idToken)))
        .subflatMap(data => dataStore.saveVerificationToken(VerificationToken(token, email, userId))
          .flatMap(saved => Either.cond(saved, (), DatabaseSaveError("Error saving verification token"))))
        .flatMapF(_ => emailSender.sendVerificationEmail(email, token.toString, user.userProfile.firstName.getOrElse("Unknown")))
        .value
    }
  }
}
