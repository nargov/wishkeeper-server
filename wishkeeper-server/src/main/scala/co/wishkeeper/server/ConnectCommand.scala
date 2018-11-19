package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.Events.UserConnected
import co.wishkeeper.server.user.commands.UserCommand
import org.joda.time.DateTime

case class ConnectGoogleUser(accessToken: String, idToken: String, sessionId: UUID) extends ConnectCommand {
  override def process(user: User): List[Events.UserEvent] = UserConnected(user.id, DateTime.now(), sessionId) :: Nil
}

case class ConnectFirebaseUser(idToken: String, sessionId: UUID, email: String)

trait ConnectCommand extends UserCommand {
  def sessionId: UUID
}
