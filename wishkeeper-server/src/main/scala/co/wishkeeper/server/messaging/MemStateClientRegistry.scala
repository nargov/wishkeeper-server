package co.wishkeeper.server.messaging

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.{ExecutionContext, Future}


trait ClientRegistry {
  type MessageDispatcher = String => Unit

  def add(userId: UUID, connection: MessageDispatcher): UUID

  def remove(userId: UUID, connectionId: UUID): Unit

  def connectedClients: Int
}

trait ClientNotifier {
  def sendTo(message: ServerNotification, userId: UUID): Unit

  def addListener(dispatch: ClientRegistryEvent => Unit)
}

class MemStateClientRegistry(implicit ec: ExecutionContext) extends ClientRegistry with ClientNotifier {

  private val connections = new AtomicReference[Map[UUID, (UUID, MessageDispatcher)]](Map.empty)
  private val listeners = new AtomicReference[Set[ClientRegistryEvent => Unit]](Set.empty)

  def add(userId: UUID, connection: MessageDispatcher): UUID = {
    val connectionId = UUID.randomUUID()
    connections.set(connections.get() + (userId -> (connectionId, connection)))
    listeners.get().foreach(_.apply(UserConnectionAdded(userId)))
    connectionId
  }

  def remove(userId: UUID, connectionId: UUID): Unit = {
    connections.get().get(userId).filter(_._1 == connectionId).foreach { _ =>
      connections.updateAndGet(_ - userId)
      listeners.get().foreach(_.apply(UserConnectionRemoved(userId)))
    }
  }

  def sendTo(message: ServerNotification, userId: UUID): Unit = Future {
    connections.get().get(userId).foreach(_._2.apply(ServerNotification.toJson(message)))
  }

  def connectedClients: Int = connections.get().size

  override def addListener(dispatch: ClientRegistryEvent => Unit): Unit = listeners.updateAndGet(_ + dispatch)
}

sealed trait ClientRegistryEvent

case class UserConnectionAdded(userId: UUID) extends ClientRegistryEvent

case class UserConnectionRemoved(userId: UUID) extends ClientRegistryEvent