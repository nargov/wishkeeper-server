package co.wishkeeper.server.web

import java.util.UUID

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.javadsl.server.Rejections
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.model.{HttpRequest, StatusCodes}
import akka.http.scaladsl.server.Directives.{as, complete, entity, get, headerValueByName, path, pathPrefix, post, _}
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.{DebuggingDirectives, LoggingMagnet}
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.http.scaladsl.{Http, HttpExt}
import akka.stream.scaladsl.{Flow, Sink, Source, SourceQueueWithComplete, StreamConverters}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.util.Timeout
import co.wishkeeper.json._
import co.wishkeeper.server.api.{ManagementApi, PublicApi}
import co.wishkeeper.server.image.ImageMetadata
import co.wishkeeper.server.messaging.ClientRegistry
import co.wishkeeper.server.search.SearchQuery
import co.wishkeeper.server.user._
import co.wishkeeper.server.user.commands._
import co.wishkeeper.server.web.WebApi.{imageDimensionsHeader, sessionIdHeader}
import co.wishkeeper.server.{ConnectFirebaseUser, ConnectGoogleUser, Error, GeneralError, GeneralSettings}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import org.joda.time.LocalDate
import org.joda.time.format.DateTimeFormat

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

class WebApi(publicApi: PublicApi, managementApi: ManagementApi, clientRegistry: ClientRegistry)
            (implicit system: ActorSystem, mat: ActorMaterializer, ec: ExecutionContextExecutor) {

  private implicit val timeout: Timeout = 4.seconds

  private implicit val circeConfig = Configuration.default.withDefaults

  private implicit val uuidUnmarshaller: Unmarshaller[String, UUID] = Unmarshaller.apply[String, UUID](ec => uuid => Try {
    UUID.fromString(uuid)
  }.fold(Future.failed, Future.successful))

  val printer: LoggingAdapter => HttpRequest => RouteResult => Unit = logging => req => res => {
    logging.info(req.toString)
    logging.info(res.toString)
  }

  val handleErrors: Error => Route = {
    case err: WishNotFound => complete(StatusCodes.NotFound, err)
    case err: InvalidStatusChange => complete(StatusCodes.Conflict, validationErrorEncoder(err).noSpaces)
    case EmailTokenAlreadyVerified => redirect(WebApi.emailConfirmationAlreadyVerified, StatusCodes.MovedPermanently)
    case err: ValidationError => complete(StatusCodes.InternalServerError, err)
    case err@NotFriends => complete(StatusCodes.Forbidden, err)
    case NoChange => complete(StatusCodes.OK)
    case GeneralError(msg) => complete(StatusCodes.InternalServerError, msg)
    case _ => complete(StatusCodes.InternalServerError)
  }

  val handleCommandResult: Either[Error, Unit] => Route = _.fold(handleErrors, _ => complete(StatusCodes.OK))

  val userIdFromSessionId: String => Directive1[UUID] = sessionId =>
    Try(UUID.fromString(sessionId)).toOption.
      flatMap(publicApi.userIdForSession).
      map(provide).
      getOrElse(reject(Rejections.authorizationFailed))

  val userIdFromSessionHeader: Directive1[UUID] = headerValueByName(sessionIdHeader).flatMap(userIdFromSessionId)

  val grantWish: (UUID, UUID) => Route = (userId, wishId) =>
    (post & pathPrefix("grant")) {
      parameter("granter" ?) { granterId =>
        handleCommandResult(publicApi.grantWish(userId, wishId, granterId.map(UUID.fromString)))
      }
    }

  val reserveWish: (UUID, UUID, UUID) => Route = (userId, friendId, wishId) =>
    post {
      handleCommandResult(publicApi.reserveWish(userId, friendId, wishId))
    }

  val unreserveWish: (UUID, UUID, UUID) => Route = (userId, friendId, wishId) =>
    delete {
      handleCommandResult(publicApi.unreserveWish(userId, friendId, wishId))
    }

  val wishReservation: (UUID, UUID, UUID) => Route = (userId, friendId, wishId) =>
    pathPrefix("reserve") {
      reserveWish(userId, friendId, wishId) ~
        unreserveWish(userId, friendId, wishId)
    }

  val getWish: (UUID, UUID) => Route = (userId, wishId) =>
    get {
      publicApi.wishById(userId, wishId).fold(handleErrors, complete(_))
    }

  val wishes: UUID => Route = userId =>
    pathPrefix("wishes") {
      pathPrefix(JavaUUID) { wishId =>
        grantWish(userId, wishId) ~
          getWish(userId, wishId)
      }
    }

  val getFriendWish: (UUID, UUID, UUID) => Route = (userId, friendId, wishId) => get {
    publicApi.wishById(userId, friendId, wishId).fold(handleErrors, complete(_))
  }

  val friendWishes: (UUID, UUID) => Route = (userId, friendId) =>
    pathPrefix("wishes") {
      pathPrefix(JavaUUID) { wishId =>
        wishReservation(userId, friendId, wishId) ~
          getFriendWish(userId, friendId, wishId)
      }
    }

  val myId: UUID => Route = id => (get & path("id")) {
    complete(id)
  }

  val in: Sink[Message, NotUsed] = Flow[Message].to(Sink.ignore)
  val out: Source[Message, SourceQueueWithComplete[Message]] = Source.queue(10, OverflowStrategy.fail)
  val handler: UUID => Flow[Message, Message, Any] = userId => Flow.fromSinkAndSourceMat(in, out)((_, outbound) => {
    val connectionId = clientRegistry.add(userId, message => outbound.offer(TextMessage(message)))
    outbound.watchCompletion().foreach(_ => clientRegistry.remove(userId, connectionId))
  })
  val websocket: Route = pathPrefix("ws") {
    parameter(sessionIdHeader) { sessionId =>
      userIdFromSessionId(sessionId) { userId =>
        handleWebSocketMessages(handler(userId))
      }
    }
  }

  val sendFriendRequest: UUID => Route = userId =>
    (put & entity(as[SendFriendRequest])) { friendRequest =>
      handleCommandResult(publicApi.sendFriendRequest(userId, friendRequest))
    }

  val removeFriend: UUID => Route = userId =>
    (delete & pathPrefix(JavaUUID)) { friendId =>
      handleCommandResult(publicApi.removeFriend(userId, friendId))
    }

  val birthdayToday: UUID => Route = userId =>
    (get & pathPrefix("birthday-today")) {
      publicApi.friendsBornToday(userId).fold(handleErrors, result => complete(result))
    }

  val potentialFriends: UUID => Route = userId => (pathPrefix("potential") & get) {
    (pathPrefix("google") & headerValueByName("gAccessToken")) { accessToken =>
      publicApi.googlePotentialFriends(userId, accessToken).fold(handleErrors, complete(_))
    }
  }

  val upcomingBirthdays: UUID => Route = userId => (pathPrefix("bday-up") & get) {
    publicApi.friendsWithUpcomingBirthdays(userId).fold(handleErrors, complete(_))
  }

  val friends: UUID => Route = userId => pathPrefix("friends") {
    sendFriendRequest(userId) ~
      removeFriend(userId) ~
      birthdayToday(userId) ~
      potentialFriends(userId) ~
      upcomingBirthdays(userId)
  }

  val setNotificationId: UUID => Route = userId => (post & pathPrefix("id") & formField("id")) { notificationId =>
    handleCommandResult(publicApi.setNotificationId(userId, notificationId))
  }

  val markViewedNotification: (UUID, UUID) => Route = (userId, notificationId) => (pathPrefix("viewed") & post) {
    handleCommandResult(publicApi.markNotificationAsViewed(userId, notificationId))
  }

  val notification: UUID => Route = userId => pathPrefix(JavaUUID) { notificationId =>
    markViewedNotification(userId, notificationId)
  }

  val notifications: UUID => Route = userId => pathPrefix("notifications") {
    setNotificationId(userId) ~
      notification(userId)
  }

  val search: UUID => Route = userId => pathPrefix("search") {
    entity(as[SearchQuery]) { query =>
      publicApi.searchUser(userId, query).fold(handleErrors, complete(_))
    }
  }

  val setName: UUID => Route = userId => (pathPrefix("name") & post) {
    entity(as[SetUserName]) { setUserName =>
      handleCommandResult(publicApi.setUserName(userId, setUserName))
    }
  }

  val setPicture: UUID => Route = userId => (pathPrefix("picture") & post) {
    fileUpload("file") { case (metadata, byteSource) =>
      val inputStream = byteSource.runWith(StreamConverters.asInputStream())
      handleCommandResult(publicApi.uploadProfileImage(inputStream, ImageMetadata(metadata.contentType.value, metadata.fileName), userId))
    }
  }

  val setGender: UUID => Route = userId => (pathPrefix("gender") & post) {
    entity(as[SetGender]) { setGender =>
      handleCommandResult(publicApi.setGender(setGender, userId))
    }
  }

  val setBirthday: UUID => Route = userId => (pathPrefix("birthday") & post) {
    formField('date) { birthday =>
      handleCommandResult(
        Try(LocalDate.parse(birthday, DateTimeFormat.forPattern("yyyy-MM-dd"))).toEither.left.map(err => GeneralError(err.getMessage))
          .flatMap(publicApi.setBirthday(userId, _)))
    }
  }

  val setAnniversary: UUID => Route = userId => (pathPrefix("anniversary") & post) {
    formField('date) { birthday =>
      handleCommandResult(
        Try(LocalDate.parse(birthday, DateTimeFormat.forPattern("yyyy-MM-dd"))).toEither.left.map(err => GeneralError(err.getMessage))
          .flatMap(publicApi.setAnniversary(userId, _)))
    }
  }

  val profile: UUID => Route = userId => pathPrefix("profile") {
    setName(userId) ~
      setPicture(userId) ~
      setGender(userId) ~
      setBirthday(userId) ~
      setAnniversary(userId)
  }

  val generalSettings: UUID => Route = userId => pathPrefix("general") {
    (post & entity(as[GeneralSettings])) { settings =>
      handleCommandResult(publicApi.setGeneralSettings(userId, settings))
    } ~
      get {
        publicApi.getGeneralSettings(userId).fold(handleErrors, complete(_))
      }
  }

  val settings: UUID => Route = userId => pathPrefix("settings") {
    generalSettings(userId)
  }

  val history: (UUID, Option[UUID]) => Route = (userId, maybeFriendId) => pathPrefix("history") {
    maybeFriendId.fold(
      publicApi.historyFor(userId).fold(handleErrors, complete(_)))(friendId =>
      publicApi.historyFor(userId, friendId).fold(handleErrors, complete(_)))

  }


  val connectGoogleUser: Route = pathPrefix("google") {
    entity(as[ConnectGoogleUser]) { connect => handleCommandResult(publicApi.connectGoogleUser(connect)) }
  }

  val connectFirebaseUser: Route = pathPrefix("firebase") {
    entity(as[ConnectFirebaseUser]) { connect =>
      handleCommandResult(publicApi.connectFirebaseUser(connect.sessionId, connect.idToken, connect.email))
    }
  }

  val createUserByEmail: Route = (pathPrefix("email") & post) {
    entity(as[CreateUserEmailFirebase]) { command =>
      onComplete(publicApi.createUserWithEmail(command)) {
        case Success(value) => handleCommandResult(value)
        case Failure(exception) => handleErrors(GeneralError(exception.getMessage))
      }
    }
  }

  val verifyEmail: Route = pathPrefix("email-confirm") {
    parameter('t.as[UUID]) { token =>
      publicApi.verifyEmail(token).fold(handleErrors, _ => {
        redirect(WebApi.emailConfirmationPage, StatusCodes.MovedPermanently)
      })
    }
  }

  val resendVerificationEmail: Route = (pathPrefix("resend-confirm-email") & post) {
    entity(as[ResendVerificationEmail]) { resend =>
      onComplete(publicApi.resendVerificationEmail(resend.email, resend.idToken)) {
        case Success(v) => handleCommandResult(v)
        case Failure(exception) => handleErrors(GeneralError(exception.getMessage))
      }
    }
  }

  val createUser: Route = pathPrefix("new") {
    createUserByEmail ~
      verifyEmail ~
      resendVerificationEmail
  }

  val connect: Route = pathPrefix("connect") {
    connectGoogleUser ~
      connectFirebaseUser ~
      createUser
  }

  val setFacebookFriendsFlag: UUID => Route = userId => path("facebook-friends") {
    handleCommandResult(publicApi.setFlagFacebookFriendsSeen(userId))
  }

  val setGoogleFriendsFlag: UUID => Route = userId => path("google-friends") {
    handleCommandResult(publicApi.setFlagGoogleFriendsSeen(userId))
  }

  val flags: UUID => Route = userId => pathPrefix("flags") {
    post {
      setFacebookFriendsFlag(userId) ~
        setGoogleFriendsFlag(userId)
    }
  }

  val homeScreenData: UUID => Route = userId => (pathPrefix("home") & get) {
    onComplete(publicApi.homeScreenData(userId).value) {
      case Success(v) => v.fold(handleErrors, complete(_))
      case Failure(e) => handleErrors(GeneralError(e.getMessage))
    }
  }

  val imageDimensions: Route = (pathPrefix("image-dims") & post) {
    parameter('url) { url =>
      onComplete(publicApi.imageDimensionsFor(url).value) {
        case Success(v) => v.fold(handleErrors, complete(_))
        case Failure(e) => handleErrors(GeneralError(e.getMessage))
      }
    }
  }

  val images: Route = pathPrefix("images") {
    (post & parameter('url)) { url =>
      publicApi.uploadImage(url).fold(handleErrors, complete(_))
    }
  }

  val newUserRoute: Route =
    userIdFromSessionHeader { userId =>
      pathPrefix("me") {
        wishes(userId) ~
          friends(userId) ~
          myId(userId) ~
          notifications(userId) ~
          profile(userId) ~
          settings(userId) ~
          history(userId, None) ~
          flags(userId) ~
          homeScreenData(userId)
      } ~
        pathPrefix(JavaUUID) { friendId =>
          friendWishes(userId, friendId) ~
            history(userId, Option(friendId))
        } ~
        search(userId) ~
        imageDimensions ~
        images
    } ~
      websocket ~
      connect

  val userRoute: Route = DebuggingDirectives.logRequestResult(LoggingMagnet(printer)) {
    pathPrefix("users") {
      path("connect" / "facebook") {
        post {
          entity(as[ConnectFacebookUser]) { connectUser ⇒
            onSuccess(publicApi.connectFacebookUser(connectUser)) { isValid =>
              if (isValid) complete(StatusCodes.OK)
              else reject(AuthorizationFailedRejection)
            }
          }
        }
      } ~
        headerValueByName(sessionIdHeader) { sessionId =>
          val sessionUUID = Option(UUID.fromString(sessionId))
          pathPrefix(JavaUUID) { userId =>
            (path("friends") & get) {
              sessionUUID.map(publicApi.friendsListFor(_, userId)).map {
                case Right(userFriends) => complete(userFriends)
                case Left(reason) => complete(StatusCodes.InternalServerError -> reason)
              }.get
            } ~
              delete {
                sessionUUID.map {
                  publicApi.unfriend(_, userId) match {
                    case Right(_) => complete(StatusCodes.OK)
                    case Left(reason) => complete(StatusCodes.BadRequest -> reason)
                  }
                }.get
              }
          } ~
            pathPrefix("profile") {
              path("facebook") {
                post {
                  entity(as[SetFacebookUserInfo]) { info =>
                    publicApi.processCommand(info, sessionUUID) //TODO move the UUID parsing to a custom directive
                    complete(StatusCodes.OK)
                  }
                }
              } ~
                get {
                  pathEnd {
                    publicApi.userProfileFor(UUID.fromString(sessionId)).
                      map(complete(_)).
                      getOrElse(reject(AuthorizationFailedRejection))
                  } ~
                    pathPrefix(JavaUUID) { friendId =>
                      sessionUUID.
                        map(publicApi.userProfileFor(_, friendId)).
                        map {
                          case Right(profile) => complete(profile)
                          case Left(reason) if reason == NotFriends => complete(StatusCodes.Forbidden -> reason)
                        }.get
                    }
                }
            } ~
            pathPrefix("friends") {
              get {
                pathEnd {
                  sessionUUID.map(publicApi.friendsListFor).map(complete(_)).get
                } ~
                  path("facebook") {
                    headerValueByName(WebApi.facebookAccessTokenHeader) { accessToken =>
                      publicApi.facebookPotentialFriends(accessToken, sessionUUID.get).
                        map(onSuccess(_) {
                          complete(_)
                        }).get //TODO test for rejection if user not found
                    }
                  }
              } ~
                (path("request") & post) {
                  //TODO deprecated
                  entity(as[SendFriendRequest]) { sendFriendRequest =>
                    publicApi.processCommand(sendFriendRequest, sessionUUID)
                    complete(StatusCodes.OK)
                  }
                }
            } ~
            pathPrefix("wishes") {
              (post & entity(as[SetWishDetails])) { setWishDetails =>
                userIdFromSessionHeader { userId =>
                  handleCommandResult(publicApi.processCommand(setWishDetails, userId))
                }
              } ~
                get {
                  pathEnd {
                    sessionUUID.flatMap(publicApi.wishListFor).map(complete(_)).get
                  } ~
                    path(JavaUUID) { friendId =>
                      sessionUUID.map(publicApi.wishListFor(_, friendId)).map {
                        case Right(userWishes) => complete(userWishes)
                        case Left(err) if err == NotFriends => complete(StatusCodes.Forbidden -> err)
                      }.get
                    }
                } ~
                (delete & path(JavaUUID)) { wishId =>
                  userIdFromSessionHeader { userId =>
                    handleCommandResult(publicApi.deleteWish(userId, wishId))
                  }
                } ~
                pathPrefix(JavaUUID / "image") { wishId =>
                  post {
                    withRequestTimeout(2.minutes) {
                      headerValueByName(imageDimensionsHeader) { imageDimensionsHeader =>
                        val imageWidth :: imageHeight :: Nil = imageDimensionsHeader.split(",").toList
                        pathEnd {
                          fileUpload("file") { case (metadata, byteSource) =>
                            val inputStream = byteSource.runWith(StreamConverters.asInputStream())
                            publicApi.uploadImage(inputStream,
                              ImageMetadata(
                                metadata.contentType.value,
                                metadata.fileName,
                                imageWidth.toInt,
                                imageHeight.toInt),
                              wishId, UUID.fromString(sessionId)).
                              map(_ => complete(StatusCodes.Created)).get //TODO Handle upload failure

                          }
                        } ~
                          path("url") {
                            parameters('filename, 'contentType, 'url) { (filename, contentType, url) =>
                              sessionUUID.map { sessionId =>
                                val uploadResult = publicApi.uploadImage(
                                  url,
                                  ImageMetadata(contentType, filename, imageWidth.toInt, imageHeight.toInt),
                                  wishId,
                                  sessionId) //TODO handle error
                                uploadResult match {
                                  case Success(_) => complete(StatusCodes.Created)
                                  case Failure(e) => throw e
                                }
                              }.get
                            }
                          }
                      }
                    }
                  } ~
                    delete {
                      sessionUUID.map { sessionId =>
                        complete(publicApi.deleteWishImage(sessionId, wishId))
                      }.get
                    }
                }
            } ~
            pathPrefix("flags") {
              post {
                path("facebook-friends") {
                  publicApi.processCommand(SetFlagFacebookFriendsListSeen(), sessionUUID)
                  complete(StatusCodes.OK)
                }
              } ~
                get {
                  sessionUUID.map { sessionId =>
                    complete(publicApi.userFlagsFor(sessionId))
                  }.get
                }
            } ~
            pathPrefix("notifications") {
              get {
                sessionUUID.map { sessionId =>
                  complete(publicApi.notificationsFor(sessionId))
                }.get
              } ~
                post {
                  pathPrefix("friendreq" / JavaUUID) { reqId =>
                    path("approve") {
                      sessionUUID.map { sessionId =>
                        publicApi.approveFriendRequest(sessionId, reqId)
                        complete(StatusCodes.OK)
                      }.get
                    } ~
                      path("ignore") {
                        sessionUUID.map { sessionId =>
                          publicApi.ignoreFriendRequest(sessionId, reqId)
                          complete(StatusCodes.OK)
                        }.get
                      }
                  } ~
                    pathPrefix("all" / "viewed") {
                      sessionUUID.map { sessionId =>
                        publicApi.markAllNotificationsViewed(sessionId)
                        complete(StatusCodes.OK)
                      }.get
                    }
                }
            }
        }
    } ~
      (path("uuid") & get) {
        complete(UUID.randomUUID())
      } ~
      newUserRoute
  }

  private var bindings: Seq[Future[ServerBinding]] = Seq.empty

  def start(port: Int = WebApi.defaultPort, managementPort: Int = WebApi.defaultManagementPort): Unit = {
    val httpExt: HttpExt = Http()
    bindings = List(
      httpExt.bindAndHandle(userRoute, "0.0.0.0", port), //TODO replace IP with parameter
      httpExt.bindAndHandle(ManagementRoute(managementApi, clientRegistry), "0.0.0.0", managementPort)
    )
  }

  def stop(): Unit = bindings.foreach(_.map(_.unbind()))
}

object WebApi {
  val defaultPort = 12300
  val defaultManagementPort = 12400

  val facebookAccessTokenHeader = "fbat"
  val sessionIdHeader = "wsid"
  val imageDimensionsHeader = "image-dim"

  val emailConfirmationPage = "https://wishkeeper.co/en/email-confirmed/"
  val emailConfirmationAlreadyVerified = "https://wishkeeper.co/en/email-address-already-verified/"
}