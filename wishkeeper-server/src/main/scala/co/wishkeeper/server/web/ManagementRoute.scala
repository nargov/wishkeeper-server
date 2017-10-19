package co.wishkeeper.server.web

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, StatusCodes}
import akka.http.scaladsl.server.Directives.{complete, get, path, pathPrefix, _}
import akka.http.scaladsl.server.directives.{DebuggingDirectives, LoggingMagnet}
import akka.http.scaladsl.server.{Route, RouteResult}
import co.wishkeeper.json._
import co.wishkeeper.server.api.ManagementApi
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._

object ManagementRoute {
  def apply(managementApi: ManagementApi)(implicit system: ActorSystem, circeConfig: Configuration): Route = {
    val printer: HttpRequest => RouteResult => Unit = req => res => {
      system.log.info(req.toString)
      system.log.info(res.toString)
    }

    DebuggingDirectives.logRequestResult(LoggingMagnet(_ => printer)) {
      pathPrefix("users") {
        get {
          path("facebook" / """\d+""".r) { facebookId ⇒
            managementApi.userIdFor(facebookId).
              map(complete(_)).
              getOrElse(complete(StatusCodes.NotFound))
          } ~
            path("email" / """.+@.+""".r / "id") { email =>
              managementApi.userByEmail(email).map(complete(_)).get
            } ~
            path(JavaUUID / "profile") { userId =>
              complete(managementApi.profileFor(userId))
            } ~
            path(JavaUUID / "wishes") { userId =>
              complete(managementApi.wishesFor(userId))
            }
        } ~
          path(JavaUUID / "flags" / "facebook-friends") { userId =>
            delete {
              managementApi.resetFacebookFriendsSeenFlag(userId)
              complete(StatusCodes.OK)
            }
          }
      }
    }
  }


}
