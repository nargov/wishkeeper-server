package io.wishkeeper.server

import java.util.UUID

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.circe.generic.auto._
import io.wishkeeper.server.Commands.ConnectUser
import io.wishkeeper.server.EventStoreMessages.{EventStoreMessage, PersistUserEvent, Persisted}
import io.wishkeeper.server.Events.UserConnected
import org.joda.time.DateTime

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

object Server {

  implicit val system = ActorSystem("server-system")
  private implicit val materializer = ActorMaterializer()
  private implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  private val eventStoreActor = system.actorOf(Props(classOf[EventStoreActor], new CassandraEventStore), "eventStoreActor")

  private implicit val timeout: Timeout = 4.seconds

  val route: Route =
    path("users" / "connect") {
      post {
        entity(as[ConnectUser]) { connectUser ⇒
          val userId = UUID.randomUUID()
          val userAggregate = system.actorOf(Props(classOf[UserAggregateActor], userId, eventStoreActor), s"userAggregateActor-$userId")
          onSuccess((userAggregate ? connectUser).mapTo[UserConnected]) {userConnected ⇒
            complete(StatusCodes.OK, userConnected.userId)
          }
        }
      }
    }
//    path("wishes") {
//      post {
//        entity(as[AddWish]) { addWish =>
//          val userAggregate = system.actorOf(Props(classOf[UserAggregateActor], addWish.userId, eventStoreActor))
//          onSuccess((userAggregate ? addWish).mapTo[WishCreated]) { wishCreated ⇒
//            complete(StatusCodes.Created, wishCreated.id)
//          }
//        }
//      } ~
//        get {
//          complete(StatusCodes.OK, Array.empty[Wish])
//        }
//    }

  def main(args: String*): Unit = {
    val port = 12300
    system.log.info(s"starting web server on port $port")
    Http().bindAndHandle(route, "localhost", port)
  }
}

class UserAggregateActor(userId: UUID, eventStoreActor: ActorRef) extends Actor {

  implicit private val timeout: Timeout = 4.seconds
  implicit val executionContext = context.system.dispatcher

  override def receive: Receive = {
    case ConnectUser() ⇒
      val caller = sender()
      val userConnected = UserConnected(userId, DateTime.now())
      val response = (eventStoreActor ? PersistUserEvent(userId, userConnected)).mapTo[EventStoreMessage]
      response.onSuccess {
        case Persisted ⇒ caller ! userConnected
      }

//    case AddWish(_, wish) ⇒
//      val wishCreated = WishCreated(userId, wish.id)
//      val wishNameSet = WishNameSet(wish.id, wish.name)
//      eventStoreActor ? PersistUserEvent(userId, wishCreated)
//      sender() ! wishCreated
//    case GetUserWishes ⇒
//      val events: Future[List[UserEvent]] = (eventStoreActor ? UserWishesFor(userId)).mapTo[List[UserEvent]]

  }
}

case class User(id: UUID, wishes: Vector[Wish] = Vector.empty)


case class Wish(name: String, id: UUID = UUID.randomUUID())

case object GetUserWishes

