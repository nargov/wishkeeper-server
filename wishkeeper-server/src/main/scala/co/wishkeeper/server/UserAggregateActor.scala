package co.wishkeeper.server

import java.util.UUID

import akka.actor.{Actor, ActorRef}
import akka.pattern._
import akka.util.Timeout
import co.wishkeeper.server.Commands.ConnectUser
import co.wishkeeper.server.EventStoreMessages.{EventStoreMessage, PersistUserEvents, Persisted, UpdateUserInfo}
import co.wishkeeper.server.Events.{UserConnected, UserFacebookIdSet}
import org.joda.time.DateTime

import scala.concurrent.duration._

class UserAggregateActor(userId: UUID, eventStoreActor: ActorRef) extends Actor {

  implicit private val timeout: Timeout = 4.seconds
  implicit val executionContext = context.system.dispatcher

  private var userInfo: UserInfo = UserInfo(userId, None)

  override def receive: Receive = {
    case ConnectUser(Some(facebookId)) ⇒
      val caller = sender()
      val userConnectedEvent = UserConnected(userId, DateTime.now())
      val facebookIdSetEvent = UserFacebookIdSet(userId, facebookId)
      val response = (eventStoreActor ? PersistUserEvents(userId, userConnectedEvent :: facebookIdSetEvent :: Nil)).mapTo[EventStoreMessage]
      userInfo = userInfo.copy(facebookData = Option(FacebookData(facebookId)))
      eventStoreActor ! UpdateUserInfo(facebookId, userInfo)

      response.onSuccess {
        case Persisted ⇒ caller ! userConnectedEvent
      }
  }
}