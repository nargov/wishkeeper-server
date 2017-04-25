package co.wishkeeper.server

import java.util.UUID

import akka.actor.Status.Success
import akka.actor.{Actor, ActorRef}
import akka.pattern._
import akka.util.Timeout
import co.wishkeeper.server.Commands.ConnectFacebookUser
import co.wishkeeper.server.EventStoreMessages._
import co.wishkeeper.server.Events._
import co.wishkeeper.server.UserAggregateActor.getValidUserBirthdayEvent
import org.joda.time.DateTime

import scala.concurrent.Future
import scala.concurrent.duration._

class UserAggregateActor(userId: UUID, eventStoreActor: ActorRef) extends Actor {

  implicit private val timeout: Timeout = 4.seconds
  implicit val executionContext = context.system.dispatcher

  private var userInfo: UserInfo = UserInfo(userId, None)

  override def receive: Receive = {
    case ConnectFacebookUser(facebookId, authToken) =>
      if (isTokenAuthentic(facebookId, authToken)) {
        val caller = sender()
        val sessionId = UUID.randomUUID()
        val userConnectedEvent = UserConnected(userId, DateTime.now(), sessionId)
        val facebookIdSetEvent = UserFacebookIdSet(userId, facebookId)
        val events = List(userConnectedEvent, facebookIdSetEvent)
        val savedEvents = (eventStoreActor ? SaveUserEvents(userId, events)).mapTo[EventStoreMessage]
        savedEvents.onSuccess { case Saved =>
          userInfo = userInfo.copy(facebookData = Option(FacebookData(facebookId)))
          eventStoreActor ! UpdateUserInfo(facebookId, userInfo)
          val savedUserSession = (eventStoreActor ? SaveUserSession(userId, sessionId)).mapTo[EventStoreMessage]
          savedUserSession.onSuccess {
            case Saved ⇒ caller ! userConnectedEvent
          }
        }
      }
      else {
        //TODO return an error for fraudulent token
      }

    case info: SetFacebookUserInfo =>
      val caller = sender()
      val events: Seq[UserEvent] = (info.age_range.map(range => UserAgeRangeSet(range.min, range.max)) ::
        info.birthday.flatMap(getValidUserBirthdayEvent) ::
        info.email.map(UserEmailSet) ::
        info.gender.map(UserGenderSet) ::
        info.locale.map(UserLocaleSet) ::
        info.timezone.map(UserTimeZoneSet) ::
        info.first_name.map(UserFirstNameSet) ::
        info.last_name.map(UserLastNameSet) ::
        info.name.map(UserNameSet) :: Nil).flatten

      val result = (eventStoreActor ? SaveUserEvents(userId, events)).mapTo[EventStoreMessage]
      result.onSuccess {
        case Saved => caller ! Success
      }
    //TODO report failures
  }

  private def isTokenAuthentic(facebookId: String, authToken: String): Boolean = {
    //TODO authenticate token with Facebook
    true
  }

}

object UserAggregateActor {

  //TODO This should probably move somewhere, but I don't know where yet.
  val getValidUserBirthdayEvent: String => Option[UserBirthdaySet] = day => {
    if (day.matches("""^\d{2}/\d{2}(/\d{4})?$"""))
      Some(UserBirthdaySet(day))
    else None
  }
}
