package co.wishkeeper.server.web

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.{DebuggingDirectives, LoggingMagnet}
import akka.http.scaladsl.server.{Route, RouteResult}
import co.wishkeeper.json._
import co.wishkeeper.server.api.ManagementApi
import co.wishkeeper.server.messaging.ClientRegistry
import co.wishkeeper.server.user.ValidationError
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._

object ManagementRoute {
  def apply(managementApi: ManagementApi, clientRegistry: ClientRegistry)(implicit system: ActorSystem, circeConfig: Configuration): Route = {
    val printer: HttpRequest => RouteResult => Unit = req => res => {
      system.log.info(req.toString)
      system.log.info(res.toString)
    }

    val ws: Route = complete(WebSocketsStats(clientRegistry.connectedClients))

    val stats: Route = pathPrefix("stats")(ws)

    val userByFacebookId: Route = get {
      pathPrefix("facebook" / """\d+""".r) { facebookId ⇒
        managementApi.userIdFor(facebookId).map(complete(_)).getOrElse(complete(StatusCodes.NotFound))
      }
    }

    val userByEmail: Route = get {
      pathPrefix("email" / """.+@.+""".r / "id") { email =>
        managementApi.userByEmail(email).map(complete(_)).getOrElse(complete(StatusCodes.NotFound))
      }
    }

    val deletePicture: UUID => Route = userId => (pathPrefix("picture") & delete) {
      managementApi.deleteUserPicture(userId).fold({ case err: ValidationError => complete(StatusCodes.ServerError, err) }, complete(_))
    }

    val profile: UUID => Route = userId => pathPrefix("profile") {
      get(complete(managementApi.profileFor(userId))) ~
        deletePicture(userId)
    }

    val wishes: UUID => Route = userId => pathPrefix("wishes") {
      get(complete(managementApi.wishesFor(userId)))
    }

    val resetFacebookFriends: UUID => Route = userId => pathPrefix("facebook-friends") {
      delete {
        managementApi.resetFacebookFriendsSeenFlag(userId)
        complete(StatusCodes.OK)
      }
    }

    val flags: UUID => Route = userId => pathPrefix("flags") {
      resetFacebookFriends(userId)
    }

    val events: UUID => Route = userId => (pathPrefix("events") & get) {
      complete(managementApi.userEvents(userId).map(e => (e.time, e.event)))
    }

    val user: Route = pathPrefix(JavaUUID) { userId =>
      profile(userId) ~
        wishes(userId) ~
        flags(userId) ~
        events(userId)
    }

    val search: Route = pathPrefix("search") {
      pathPrefix("rebuild") {
        managementApi.rebuildUserSearch()
        complete(StatusCodes.OK)
      }
    }

    val historyView: Route = pathPrefix("history" / "rebuild") {
      managementApi.rebuildHistoryProjection()
      complete(StatusCodes.OK)
    }

    val views: Route = pathPrefix("views") {
      search ~
      historyView
    }

    val resubscribeAll = pathPrefix("resub") {
      complete(managementApi.resubscribePeriodicWakeup())
    }

    val periodicWakeup = pathPrefix("periodic") {
      resubscribeAll
    }

    val subscriptions = pathPrefix("subs") {
      periodicWakeup
    }

    val migrateUrls = (pathPrefix("migrate-urls") & post) {
      complete(managementApi.migrateUrlsToHttp())
    }

    DebuggingDirectives.logRequestResult(LoggingMagnet(_ => printer)) {
      pathPrefix("users") {
        userByEmail ~
          userByFacebookId ~
          user
      } ~
        stats ~
      views ~
      subscriptions ~
      migrateUrls
    }
  }
}

case class WebSocketsStats(connections: Int = 0)