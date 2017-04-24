package co.wishkeeper.server

import java.util.UUID

import akka.actor.{Actor, ActorRef}
import akka.pattern._
import akka.util.Timeout
import co.wishkeeper.server.Commands.ConnectFacebookUser
import co.wishkeeper.server.EventStoreMessages.{EventStoreMessage, PersistUserEvents, Persisted, UpdateUserInfo}
import co.wishkeeper.server.Events.{UserConnected, UserFacebookIdSet}
import org.joda.time.DateTime

import scala.concurrent.duration._

class UserAggregateActor(userId: UUID, eventStoreActor: ActorRef) extends Actor {

  implicit private val timeout: Timeout = 4.seconds
  implicit val executionContext = context.system.dispatcher

  private var userInfo: UserInfo = UserInfo(userId, None)

  override def receive: Receive = {
    case ConnectFacebookUser(facebookId, authToken) =>
      if (isTokenAuthentic(facebookId, authToken)) {
        writeUserConnectedEvents(facebookId, sender())
      }
      else {
        //TODO return an error for fraudulent token
      }
  }

  private def isTokenAuthentic(facebookId: String, authToken: String): Boolean = {
    //TODO authenticate token with Facebook
    true
  }

  private def writeUserConnectedEvents(facebookId: String, sender: ActorRef) = {
    val newSessionId = UUID.randomUUID()
    val userConnectedEvent = UserConnected(userId, DateTime.now(), newSessionId)
    val facebookIdSetEvent = UserFacebookIdSet(userId, facebookId)
    val response = (eventStoreActor ? PersistUserEvents(userId, userConnectedEvent :: facebookIdSetEvent :: Nil)).mapTo[EventStoreMessage]
    userInfo = userInfo.copy(facebookData = Option(FacebookData(facebookId)))
    eventStoreActor ! UpdateUserInfo(facebookId, userInfo)

    response.onSuccess {
      case Persisted ⇒ sender ! userConnectedEvent
    }
  }
}