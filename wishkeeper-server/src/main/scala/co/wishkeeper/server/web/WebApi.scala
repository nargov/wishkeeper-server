package co.wishkeeper.server.web

import java.util.UUID

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.javadsl.server.Rejections
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.{HttpRequest, StatusCodes}
import akka.http.scaladsl.server.Directives.{as, complete, entity, get, headerValueByName, path, pathPrefix, post, _}
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.{DebuggingDirectives, LoggingMagnet}
import akka.http.scaladsl.{Http, HttpExt}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.StreamConverters
import akka.util.Timeout
import co.wishkeeper.json._
import co.wishkeeper.server.Error
import co.wishkeeper.server.api.{ManagementApi, PublicApi}
import co.wishkeeper.server.image.ImageMetadata
import co.wishkeeper.server.user.commands._
import co.wishkeeper.server.user.{InvalidStatusChange, NotFriends, ValidationError, WishNotFound}
import co.wishkeeper.server.web.WebApi.imageDimensionsHeader
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

class WebApi(publicApi: PublicApi, managementApi: ManagementApi)
            (implicit system: ActorSystem, mat: ActorMaterializer, ec: ExecutionContextExecutor) {

  private implicit val timeout: Timeout = 4.seconds

  private implicit val circeConfig = Configuration.default.withDefaults

  val printer: LoggingAdapter => HttpRequest => RouteResult => Unit = logging => req => res => {
    logging.info(req.toString)
    logging.info(res.toString)
  }

  val handleErrors: Error => Route = {
    case err: WishNotFound => complete(StatusCodes.NotFound, err)
    case err: InvalidStatusChange => complete(StatusCodes.Conflict, err)
    case err: ValidationError => complete(StatusCodes.InternalServerError, err)
    case _ => complete(StatusCodes.InternalServerError)
  }

  val handleCommandResult: Either[Error, Unit] => Route = _.fold(handleErrors, _ => complete(StatusCodes.OK))

  val userIdFromSession: Directive1[UUID] = headerValueByName(WebApi.sessionIdHeader).flatMap {
    sessionId =>
      Try(UUID.fromString(sessionId)).toOption.
        flatMap(publicApi.userIdForSession).
        map(provide).
        getOrElse(reject(Rejections.authorizationFailed))
  }

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

  val friendWishes: (UUID, UUID) => Route = (userId, friendId) =>
    pathPrefix("wishes") {
      pathPrefix(JavaUUID) { wishId =>
        wishReservation(userId, friendId, wishId)
      }
    }

  val myId: UUID => Route = id => (get & path("id")) {
    complete(id)
  }

  val newUserRoute: Route =
    userIdFromSession { userId =>
      pathPrefix("me") {
        wishes(userId) ~
          myId(userId)
      } ~
        pathPrefix(JavaUUID) { friendId =>
          friendWishes(userId, friendId)
        }
    }

  val userRoute: Route =
    DebuggingDirectives.logRequestResult(LoggingMagnet(printer)) {
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
          headerValueByName(WebApi.sessionIdHeader) { sessionId =>
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
                        publicApi.potentialFriendsFor(accessToken, sessionUUID.get).
                          map(onSuccess(_) {
                            complete(_)
                          }).get //TODO test for rejection if user not found
                      }
                    }
                } ~
                  (path("request") & post) {
                    entity(as[SendFriendRequest]) { sendFriendRequest =>
                      publicApi.processCommand(sendFriendRequest, sessionUUID)
                      complete(StatusCodes.OK)
                    }
                  }
              } ~
              pathPrefix("wishes") {
                (post & entity(as[SetWishDetails])) { setWishDetails =>
                  userIdFromSession { userId =>
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
                    userIdFromSession { userId =>
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
      httpExt.bindAndHandle(ManagementRoute(managementApi), "0.0.0.0", managementPort)
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
}