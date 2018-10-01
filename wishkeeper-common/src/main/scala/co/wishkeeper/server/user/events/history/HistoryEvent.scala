package co.wishkeeper.server.user.events.history

import java.util.UUID

import co.wishkeeper.server.ImageLinks
import org.joda.time.DateTime

sealed trait HistoryEvent

case class ReservedWish(wishId: UUID, wishOwner: UUID, ownerName: String, wishName: String, wishImage: Option[ImageLinks] = None) extends HistoryEvent

case class GrantedWish(wishId: UUID, wishOwner: UUID, ownerName: String, wishName: String, wishImage: Option[ImageLinks] = None) extends HistoryEvent

case class ReceivedWish(wishId: UUID, granter: UUID, granterName: String, wishName: String, wishImage: Option[ImageLinks] = None) extends HistoryEvent

case class HistoryEventInstance(userId: UUID, wishId: UUID, time: DateTime, event: HistoryEvent)