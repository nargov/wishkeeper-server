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
import io.wishkeeper.server.Commands.AddWish
import io.wishkeeper.server.EventStoreMessages.PersistUserEvent

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

object Server {

  implicit val system = ActorSystem("server-system")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  private val eventStoreActor = system.actorOf(Props(classOf[EventStoreActor], new CassandraEventStore))

  val route: Route =
    path("wishes") {
      post {
        entity(as[AddWish]) { addWish =>
          val userAggregate = system.actorOf(Props(classOf[UserAggregateActor], addWish.userId, eventStoreActor))
          implicit val timeout: Timeout = 4.seconds
          onSuccess((userAggregate ? addWish).mapTo[WishCreated]) { wishCreated ⇒
            complete(StatusCodes.Created, wishCreated.id)
          }
        }
      } ~
        get {
          complete(StatusCodes.OK, Array.empty[Wish])
        }
    }

  def main(args: String*): Unit = {
    Http().bindAndHandle(route, "localhost", 12300)
  }
}

class UserAggregateActor(userId: UUID, eventStoreActor: ActorRef) extends Actor {

  private var state = User(userId)

  override def receive: Receive = {
    case AddWish(_, wish) ⇒
      implicit val timeout: Timeout = 4.seconds
      val wishCreated = WishCreated(userId, wish.id, wish.name)
      eventStoreActor ? PersistUserEvent(userId, wishCreated)
      state = state.copy(wishes = state.wishes :+ wish)
      sender() ! wishCreated
  }
}

object Commands {

  case class AddWish(userId: UUID, wish: Wish)

}

case class User(id: UUID, wishes: Vector[Wish] = Vector.empty)


case class Wish(name: String, id: UUID = UUID.randomUUID())