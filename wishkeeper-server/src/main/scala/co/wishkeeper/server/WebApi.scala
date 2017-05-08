package co.wishkeeper.server

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, StatusCodes}
import akka.http.scaladsl.server.Directives.{as, complete, entity, get, headerValueByName, path, pathPrefix, post, _}
import akka.http.scaladsl.server.directives.{DebuggingDirectives, LoggingMagnet}
import akka.http.scaladsl.server.{Route, RouteResult}
import akka.http.scaladsl.{Http, HttpExt}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import co.wishkeeper.json._
import co.wishkeeper.server.Commands.{ConnectFacebookUser, SetFacebookUserInfo}
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.circe.generic.auto._

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

class WebApi(commandProcessor: CommandProcessor, userIdByFacebookIdProjection: DataStoreUserIdByFacebookIdProjection,
             userProfileProjection: UserProfileProjection, dataStore: DataStore, userFriendsProjection: UserFriendsProjection)
            (implicit system: ActorSystem, materializer: ActorMaterializer, executionContext: ExecutionContextExecutor) {

  private implicit val timeout: Timeout = 4.seconds

  val printer: HttpRequest => RouteResult => Unit = req => res => {
    system.log.info(req.toString)
    system.log.info(res.toString)
  }

  val route: Route =
    DebuggingDirectives.logRequestResult(LoggingMagnet(_ => printer)) {
      pathPrefix("users") {
        path("connect" / "facebook") {
          post {
            entity(as[ConnectFacebookUser]) { connectUser ⇒
              //TODO validate facebook token
              commandProcessor.process(connectUser)
              complete(StatusCodes.OK)
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
            path("friends" / "facebook") {
              get {
                headerValueByName(WebApi.facebookAccessTokenHeader) { accessToken =>
                  val maybeFacebookId: Option[String] = for {
                    userId <- dataStore.userBySession(UUID.fromString(sessionId))
                    socialData <- userProfileProjection.get(userId).socialData
                    facebookId <- socialData.facebookId
                  } yield facebookId

                  maybeFacebookId.
                    map(userFriendsProjection.potentialFacebookFriends(_, accessToken)).
                    map(complete(_)).
                    get
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
        path("facebook" / """\d+""".r) { facebookId ⇒
          get {
            userIdByFacebookIdProjection.get(facebookId).
              map(id ⇒ complete(id)).
              getOrElse(complete(StatusCodes.NotFound))
          }
        } ~
          get {
            path(JavaUUID / "profile") { userId =>
              complete(userProfileProjection.get(userId))
            }
          }
      }
    }

  def start(port: Int = WebApi.defaultPort, managementPort: Int = WebApi.defaultManagementPort): Unit = {
    val httpExt: HttpExt = Http()
    httpExt.bindAndHandle(route, "localhost", port)
    httpExt.bindAndHandle(managementRoute, "localhost", managementPort)
  }

}

object WebApi {
  val defaultPort = 12300
  val defaultManagementPort = 12400

  val facebookAccessTokenHeader = "fbat"
  val sessionIdHeader = "wsid"
}