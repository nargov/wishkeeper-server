package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.Events.{UserConnected, UserEvent, UserFacebookIdSet}
import org.joda.time.DateTime

object Commands {

  sealed trait UserCommand {
    def process(user: User): List[UserEvent]
  }

  case class ConnectFacebookUser(facebookId: String, authToken: String, newSessionId: Option[UUID] = None) extends UserCommand {
    override def process(user: User): List[UserEvent] = {
      val sessionId = newSessionId.getOrElse(UUID.randomUUID())
      val now = DateTime.now()
      val userConnectedEvent = UserConnected(now, sessionId)
      val facebookIdSetEvent = UserFacebookIdSet(facebookId)
      List(userConnectedEvent, facebookIdSetEvent)
    }
  }

  case class SetFacebookUserInfo(age_range: Option[FacebookAgeRange],
                                 birthday: Option[String],
                                 email: Option[String],
                                 first_name: Option[String],
                                 last_name: Option[String],
                                 name: Option[String],
                                 gender: Option[String],
                                 locale: Option[String],
                                 timezone: Option[Int]) extends UserCommand {

    override def process(user: User): List[UserEvent] = ???
  }

}
