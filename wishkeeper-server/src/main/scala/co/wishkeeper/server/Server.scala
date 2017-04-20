package co.wishkeeper.server

import java.util.UUID

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Route, RouteResult}
import akka.http.scaladsl.server.directives.{DebuggingDirectives, LoggingMagnet}
import akka.pattern._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import co.wishkeeper.json._
import co.wishkeeper.server.Commands.ConnectUser
import co.wishkeeper.server.EventStoreMessages.UserInfoForFacebookId
import co.wishkeeper.server.Events.UserConnected
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.circe.generic.auto._

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

object Server {

  implicit val system = ActorSystem("server-system")
  private implicit val materializer = ActorMaterializer()
  private implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  private val eventStoreActor = system.actorOf(Props(classOf[EventStoreActor], new CassandraEventStore), "eventStoreActor")

  private implicit val timeout: Timeout = 4.seconds

  val printer: HttpRequest => RouteResult => Unit = req => res => {
    println(req)
    println(res)
  }

  val route: Route =
    DebuggingDirectives.logRequestResult(LoggingMagnet(_ => printer)) {
      pathPrefix("users") {
        path("connect") {
          post {
            entity(as[ConnectUser]) { connectUser ⇒
              val userId = UUID.randomUUID()
              val userAggregate = system.actorOf(Props(classOf[UserAggregateActor], userId, eventStoreActor), s"userAggregateActor-$userId")
              onSuccess((userAggregate ? connectUser).mapTo[UserConnected]) { userConnected ⇒
                complete(StatusCodes.OK, ConnectResponse(User(userConnected.userId)))
              }
            }
          }
        } ~
          path("facebook" / """\d+""".r) { facebookId ⇒
            get {
              onSuccess((eventStoreActor ? UserInfoForFacebookId(facebookId)).mapTo[Option[UserInfo]]) { maybeUserInfo ⇒
                maybeUserInfo.map(info ⇒ complete(info)).get //TODO handle None case
              }
            }
          }
      }
    }

  val defaultPort = 12300

  def main(args: Array[String] = Array.empty): Unit = {
    start()
  }

  def start(port: Int = defaultPort): Unit = {
    system.log.info(s"starting web server on port $port")
    Http().bindAndHandle(route, "localhost", port)
  }

}


case class User(id: UUID, wishes: Vector[Wish] = Vector.empty)

case class Wish(name: String, id: UUID = UUID.randomUUID())

case object GetUserWishes



case class UserInfoInstance(userInfo: UserInfo, seq: Long)

case class ConnectResponse(user: User)