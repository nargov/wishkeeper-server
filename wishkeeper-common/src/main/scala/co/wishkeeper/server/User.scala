package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.Commands.UserCommand
import co.wishkeeper.server.Events.UserEvent

case class User(id: UUID) {
  def processCommand(command: UserCommand): List[UserEvent] = {
    command.process(this)
  }

  def applyEvent[T <: UserEvent](event: T)(implicit ev: EventHandler[T]): User = ev.apply(this)
}

object User {
  def createNew() = new User(UUID.randomUUID())

}

trait EventHandler[T] {
  def apply(user: User): User
}