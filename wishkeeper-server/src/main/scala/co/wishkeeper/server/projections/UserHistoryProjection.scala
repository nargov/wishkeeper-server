package co.wishkeeper.server.projections

import java.util.UUID

import co.wishkeeper.server.Events.{UserEvent, WishGranted, WishReserved, WishUnreserved}
import co.wishkeeper.server._
import co.wishkeeper.server.messaging.{ClientNotifier, HistoryUpdated}
import co.wishkeeper.server.user.events.history._

trait UserHistoryProjection extends Projection {
  def friendHistory(friendId: UUID): List[HistoryEventInstance]

  def historyFor(userId: UUID): List[HistoryEventInstance]
}

class ScanningUserHistoryProjection(dataStore: DataStore, clientNotifier: ClientNotifier) extends UserHistoryProjection with EventProcessor {
  val unnamed = "Unnamed"

  override def process[E <: UserEvent](instance: UserEventInstance[E]): List[UserEventInstance[_ <: UserEvent]] = {
    val userId = instance.userId
    val time = instance.time
    instance.event match {
      case WishReserved(wishId, reserverId) =>
        val user = User.replay(dataStore.userEvents(userId))
        val maybeWish = user.wishes.get(wishId)
        maybeWish.foreach { wish =>
          val historyEvent = ReservedWish(wishId, userId, user.userProfile.name.getOrElse(unnamed), wish.name.getOrElse(""), wish.image)
          dataStore.saveUserHistoryEvent(reserverId, time, historyEvent, wishId)
          clientNotifier.sendTo(HistoryUpdated, reserverId)
        }
      case WishUnreserved(wishId) =>
        val user = User.replay(dataStore.userEvents(userId))
        user.wishes.get(wishId).foreach { wish =>
          wish.pastReservers.foreach { reserverId =>
            dataStore.deleteWishHistoryEvent(reserverId, wishId)
            clientNotifier.sendTo(HistoryUpdated, reserverId)
          }
        }
      case WishGranted(wishId) =>
        val user = User.replay(dataStore.userEvents(userId))
        val maybeWish = user.wishes.get(wishId)
        maybeWish.foreach { wish =>
          wish.reserver.fold {
            dataStore.saveUserHistoryEvent(userId, time, ReceivedWish(wishId, userId, user.userProfile.name.getOrElse("Unknown"),
              wish.name.getOrElse(""), wish.image), wishId)
            clientNotifier.sendTo(HistoryUpdated, userId)
          } { reserver =>
            val reserverName = User.replay(dataStore.userEvents(reserver)).userProfile.name.getOrElse(unnamed)
            dataStore.deleteWishHistoryEvent(reserver, wishId)
            dataStore.saveUserHistoryEvent(reserver, time, GrantedWish(wishId, userId, user.userProfile.name.getOrElse(unnamed),
              wish.name.getOrElse(""), wish.image), wishId)
            dataStore.saveUserHistoryEvent(userId, time, ReceivedWish(wishId, reserver, reserverName, wish.name.getOrElse(""), wish.image), wishId)
            clientNotifier.sendTo(HistoryUpdated, userId)
            clientNotifier.sendTo(HistoryUpdated, reserver)
          }
        }
      case _ =>
    }
    Nil
  }

  implicit val historyOrdering: Ordering[HistoryEventInstance] = Ordering.fromLessThan[HistoryEventInstance]((i1, i2) => i1.time.isAfter(i2.time))

  override def historyFor(userId: UUID): List[HistoryEventInstance] = dataStore.historyFor(userId).sorted

  override def friendHistory(friendId: UUID): List[HistoryEventInstance] =
    historyFor(friendId).filter(_.event.isInstanceOf[ReceivedWish])

  override def rebuild(): Unit = {
    dataStore.truncateHistory()
    dataStore.allUserEvents(classOf[WishReserved], classOf[WishUnreserved], classOf[WishGranted]).foreach(instance => process(instance))
  }
}