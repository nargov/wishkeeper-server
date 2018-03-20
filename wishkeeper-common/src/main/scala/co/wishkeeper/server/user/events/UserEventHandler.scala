package co.wishkeeper.server.user.events

import co.wishkeeper.server.Events.UserEvent
import co.wishkeeper.server.User
import org.joda.time.DateTime

import scala.annotation.implicitNotFound

@implicitNotFound("No handler found for ${E}")
trait UserEventHandler[E <: UserEvent] {
  def apply(user: User, event: E, time: DateTime): User
}
