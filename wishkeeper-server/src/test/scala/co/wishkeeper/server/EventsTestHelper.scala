package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.Events.{UserConnected, UserEvent}
import org.joda.time.DateTime

object EventsTestHelper {
  def userConnectEvent(userId: UUID) = UserConnected(userId, DateTime.now(), UUID.randomUUID())

  def asEventInstants(events: List[UserEvent]): List[UserEventInstant] = events.map(event => UserEventInstant(event, DateTime.now().minusDays(1)))
}
