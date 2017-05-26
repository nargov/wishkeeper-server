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
import akka.util.Timeout
import co.wishkeeper.json._
import co.wishkeeper.server.Commands.{ConnectFacebookUser, SendFriendRequest, SetFacebookUserInfo, SetWishDetails}
import co.wishkeeper.server.projections.{DataStoreUserIdByFacebookIdProjection, UserFriendsProjection, UserProfileProjection}
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}

class WebApi(commandProcessor: CommandProcessor, userIdByFacebookIdProjection: DataStoreUserIdByFacebookIdProjection,
             userProfileProjection: UserProfileProjection, dataStore: DataStore, userFriendsProjection: UserFriendsProjection,
             facebookConnector: FacebookConnector, incomingFriendRequestsProjection: IncomingFriendRequestsProjection)
            (implicit system: ActorSystem, materializer: ActorMaterializer, executionContext: ExecutionContextExecutor) {

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
              onSuccess(facebookConnector.isValid(connectUser.authToken)) { isValid =>
                if (isValid) {
                  commandProcessor.process(connectUser)
                  complete(StatusCodes.OK)
                }
                else {
                  reject(AuthorizationFailedRejection)
                }
              }
            }
          }
        } ~
        headerValueByName(WebApi.sessionIdHeader) { sessionId =>
          path("info" / "facebook") {
            post {
              entity(as[SetFacebookUserInfo]) { info =>
                commandProcessor.process(info, Option(UUID.fromString(sessionId))) //TODO move the UUID parsing to a custom directive
                complete(StatusCodes.OK)
              }
            }
          } ~
          pathPrefix("friends") {
            (path("facebook") & get) {
              headerValueByName(WebApi.facebookAccessTokenHeader) { accessToken =>
                val maybeFacebookId: Option[String] = for {
                  userId <- dataStore.userBySession(UUID.fromString(sessionId))
                  socialData <- userProfileProjection.get(userId).socialData
                  facebookId <- socialData.facebookId
                } yield facebookId

                maybeFacebookId.
                  map(userFriendsProjection.potentialFacebookFriends(_, accessToken)).
                  map(complete(_)).
                  get //TODO test for rejection if user not found
              }
            } ~
            (path("request") & post) {
              entity(as[SendFriendRequest]) { sendFriendRequest =>
                commandProcessor.process(sendFriendRequest, Option(UUID.fromString(sessionId)))
                complete(StatusCodes.OK)
              }
            } ~
            (path("requests" / "incoming") & get) {
              dataStore.userBySession(UUID.fromString(sessionId)).
                map(incomingFriendRequestsProjection.awaitingApproval).
                map(complete(_)).
                get //TODO test for rejection if user not found
            }
          } ~
          pathPrefix("wishes") {
            post {
              entity(as[SetWishDetails]) { setWishDetails =>
                commandProcessor.process(setWishDetails, Option(UUID.fromString(sessionId)))
                complete(StatusCodes.OK)
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
            userIdByFacebookIdProjection.get(facebookId).
              map(id ⇒ complete(id)).
              getOrElse(complete(StatusCodes.NotFound))
          } ~
          path(JavaUUID / "profile") { userId =>
              complete(userProfileProjection.get(userId))
          } ~
          path(JavaUUID / "wishes") { userId =>
            complete(User.replay(dataStore.userEventsFor(userId)).wishes.values)
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
}