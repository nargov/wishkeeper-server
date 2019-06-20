package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.Events.{SessionPlatformSet, UserConnected}
import co.wishkeeper.server.user.Platform
import co.wishkeeper.server.user.commands.UserCommand
import org.joda.time.DateTime

case class ConnectGoogleUser(accessToken: String, idToken: String, sessionId: UUID, platform: Option[Platform] = None) extends ConnectCommand {
  override def process(user: User): List[Events.UserEvent] = {
    val platformSetEvent = SessionPlatformSet(sessionId, platform.getOrElse(Platform.Android))
    UserConnected(user.id, DateTime.now(), sessionId) :: platformSetEvent :: Nil
  }
}

case class ConnectFirebaseUser(idToken: String, sessionId: UUID, email: String)

trait ConnectCommand extends UserCommand {
  def sessionId: UUID
}
