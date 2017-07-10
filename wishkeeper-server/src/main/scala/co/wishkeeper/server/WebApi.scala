package co.wishkeeper.server

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.{HttpRequest, StatusCodes}
import akka.http.scaladsl.server.Directives.{as, complete, entity, get, headerValueByName, path, pathPrefix, post, _}
import akka.http.scaladsl.server.directives.{DebuggingDirectives, LoggingMagnet}
import akka.http.scaladsl.server.{AuthorizationFailedRejection, Route, RouteResult}
import akka.http.scaladsl.{Http, HttpExt}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.StreamConverters
import akka.util.Timeout
import co.wishkeeper.json._
import co.wishkeeper.server.Commands._
import co.wishkeeper.server.WebApi.imageDimensionsHeader
import co.wishkeeper.server.api.{ManagementApi, PublicApi}
import co.wishkeeper.server.image.ImageMetadata
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}

class WebApi(publicApi: PublicApi, managementApi: ManagementApi)(implicit system: ActorSystem, mat: ActorMaterializer, ec: ExecutionContextExecutor) {

  private implicit val timeout: Timeout = 4.seconds

  private implicit val circeConfig = Configuration.default.withDefaults

  val printer: HttpRequest => RouteResult => Unit = req => res => {
    system.log.info(req.toString)
    system.log.info(res.toString)
  }

  val userRoute: Route =
    DebuggingDirectives.logRequestResult(LoggingMagnet(_ => printer)) {
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
            pathPrefix("profile") {
              path("facebook") {
                post {
                  entity(as[SetFacebookUserInfo]) { info =>
                    publicApi.processCommand(info, sessionUUID) //TODO move the UUID parsing to a custom directive
                    complete(StatusCodes.OK)
                  }
                }
              } ~
                pathEnd {
                  get {
                    publicApi.userProfileFor(UUID.fromString(sessionId)).
                      map(complete(_)).
                      getOrElse(reject(AuthorizationFailedRejection))
                  }
                }
            } ~
              pathPrefix("friends") {
                (path("facebook") & get) {
                  headerValueByName(WebApi.facebookAccessTokenHeader) { accessToken =>
                    publicApi.potentialFriendsFor(accessToken, sessionUUID.get).
                      map(onSuccess(_) {
                        complete(_)
                      }).get //TODO test for rejection if user not found
                  }
                } ~
                  (path("request") & post) {
                    entity(as[SendFriendRequest]) { sendFriendRequest =>
                      publicApi.processCommand(sendFriendRequest, sessionUUID)
                      complete(StatusCodes.OK)
                    }
                  } ~
                  (path("requests" / "incoming") & get) {
                    publicApi.incomingFriendRequestSenders(UUID.fromString(sessionId)).
                      map(complete(_)).get //TODO test for rejection if user not found
                  }
              } ~
              pathPrefix("wishes") {
                post {
                  entity(as[SetWishDetails]) { setWishDetails =>
                    publicApi.processCommand(setWishDetails, sessionUUID)
                    complete(StatusCodes.OK)
                  }
                } ~
                  get {
                    sessionUUID.flatMap(publicApi.wishListFor).map(complete(_)).get
                  } ~
                  delete {
                    path(JavaUUID) { wishId =>
                      sessionUUID.map { sessionId =>
                        publicApi.deleteWish(sessionId, wishId)
                        complete()
                      }.get
                    }
                  } ~
                  path(JavaUUID / "image") { wishId =>
                    post {
                      headerValueByName(imageDimensionsHeader) { imageDimensionsHeader =>
                        val imageWidth :: imageHeight :: Nil = imageDimensionsHeader.split(",").toList
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
                      }
                    }
                  }
              }
          }
      } ~
        (path("uuid") & get) {
          complete(UUID.randomUUID())
        }
    }


  val managementRoute: Route =
    DebuggingDirectives.logRequestResult(LoggingMagnet(_ => printer)) {
      pathPrefix("users") {
        get {
          path("facebook" / """\d+""".r) { facebookId ⇒
            managementApi.userIdFor(facebookId).
              map(complete(_)).
              getOrElse(complete(StatusCodes.NotFound))
          } ~
            path(JavaUUID / "profile") { userId =>
              complete(managementApi.profileFor(userId))
            } ~
            path(JavaUUID / "wishes") { userId =>
              complete(managementApi.wishesFor(userId))
            }
        }
      }
    }

  private var bindings: Seq[Future[ServerBinding]] = Seq.empty

  def start(port: Int = WebApi.defaultPort, managementPort: Int = WebApi.defaultManagementPort): Unit = {
    val httpExt: HttpExt = Http()
    bindings = List(
      httpExt.bindAndHandle(userRoute, "0.0.0.0", port), //TODO replace IP with parameter
      httpExt.bindAndHandle(managementRoute, "0.0.0.0", managementPort)
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