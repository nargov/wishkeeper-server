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
import co.wishkeeper.server.Commands.ConnectFacebookUser
import co.wishkeeper.server.Events._
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.circe.generic.auto._
import org.joda.time.DateTime

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

class WebApi(eventStore: EventStore) {
  private implicit val system = ActorSystem("web-api")
  private implicit val materializer = ActorMaterializer()
  private implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  private implicit val timeout: Timeout = 4.seconds

  val printer: HttpRequest => RouteResult => Unit = req => res => {
    println(req)
    println(res)
  }

  val route: Route =
    DebuggingDirectives.logRequestResult(LoggingMagnet(_ => printer)) {
      pathPrefix("users") {
        path("connect" / "facebook") {
          post {
            entity(as[ConnectFacebookUser]) { connectUser ⇒
              //TODO validate facebook token
              val userId = UUID.randomUUID()
              val sessionId = UUID.randomUUID()
              val now = DateTime.now()
              val userConnectedEvent = UserConnected(userId, now, sessionId)
              val facebookIdSetEvent = UserFacebookIdSet(userId, connectUser.facebookId)
              val events = List(userConnectedEvent, facebookIdSetEvent)
              val lastSeqNum = eventStore.lastSequenceNum(userId)
              eventStore.saveUserEvents(userId, lastSeqNum, now, events)
              val userInfo = UserInfo(userId, Option(FacebookData(connectUser.facebookId)))
              eventStore.updateFacebookIdToUserInfo(connectUser.facebookId, None, userInfo) //TODO get last seq number from table
              eventStore.saveUserSession(userId, sessionId, now)
              complete(StatusCodes.OK, ConnectResponse(userId, sessionId))
            }
          }
        } ~
          headerValueByName("wsid") { sessionId =>
            //TODO validate session
            path("info" / "facebook") {
              post {
                entity(as[SetFacebookUserInfo]) { info =>
                  val maybeUserId: Option[UUID] = eventStore.userBySession(UUID.fromString(sessionId))
                  maybeUserId.map { userId =>
                    val events: Seq[UserEvent] = (info.age_range.map(range => UserAgeRangeSet(range.min, range.max)) ::
                      info.birthday.flatMap(WebApi.getValidUserBirthdayEvent) ::
                      info.email.map(UserEmailSet) ::
                      info.gender.map(UserGenderSet) ::
                      info.locale.map(UserLocaleSet) ::
                      info.timezone.map(UserTimeZoneSet) ::
                      info.first_name.map(UserFirstNameSet) ::
                      info.last_name.map(UserLastNameSet) ::
                      info.name.map(UserNameSet) :: Nil).flatten

                    eventStore.saveUserEvents(userId, eventStore.lastSequenceNum(userId), DateTime.now(), events)
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

  val getValidUserBirthdayEvent: String => Option[UserBirthdaySet] = day => {
    if (day.matches("""^\d{2}/\d{2}(/\d{4})?$"""))
      Some(UserBirthdaySet(day))
    else None
  }
}