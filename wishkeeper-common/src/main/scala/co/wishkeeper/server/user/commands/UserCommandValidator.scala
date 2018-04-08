package co.wishkeeper.server.user.commands

import co.wishkeeper.server.User
import co.wishkeeper.server.user.ValidationError

trait UserCommandValidator[C <: UserCommand] {
  def validate(user: User, command: C): Either[ValidationError, Unit]
}
