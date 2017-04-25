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

class WebApi(eventStore: EventStore, sessionManager: SessionManager) {
  private implicit val system = ActorSystem("web-api")
  private implicit val materializer = ActorMaterializer()
  private implicit val executionContext: ExecutionContextExecutor = system.dispatcher

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
              val userSession = sessionManager.saveUserSessionConnected(connectUser)
              complete(StatusCodes.OK, ConnectResponse(userSession.userId, userSession.sessionId))
            }
          }
        } ~
          headerValueByName("wsid") { sessionId =>
             //TODO validate session
            path("info" / "facebook") {
              post {
                entity(as[SetFacebookUserInfo]) { info =>
                  val maybeUserId: Option[UUID] = sessionManager.userIdForSession(UUID.fromString(sessionId))
                  maybeUserId.map { userId =>
                    sessionManager.saveFacebookUserInfo(info, userId)
                    complete(StatusCodes.OK)
                  }.getOrElse(complete(StatusCodes.Unauthorized))
                }
              }
            }
          }
      }
    }


  val managementRoute: Route =
    DebuggingDirectives.logRequestResult(LoggingMagnet(_ => printer)) {
      pathPrefix("users") {
        path("facebook" / """\d+""".r) { facebookId ⇒
          get {
            val maybeUserInfo: Option[UserInfoInstance] = eventStore.userInfoByFacebookId(facebookId)
            maybeUserInfo.map(info ⇒ complete(info.userInfo)).get //TODO handle None case
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
}

case class ConnectResponse(userId: UUID, sessionId: UUID) {
  //TODO add def fromUserConnected
}
