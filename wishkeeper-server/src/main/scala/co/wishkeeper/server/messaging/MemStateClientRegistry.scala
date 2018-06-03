package co.wishkeeper.server.messaging

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference


trait ClientRegistry {
  type MessageDispatcher = String => Unit

  def add(userId: UUID, connection: MessageDispatcher): UUID

  def remove(userId: UUID, connectionId: UUID): Unit

  def connectedClients: Int
}

trait ClientNotifier {
  def sendTo(message: ServerNotification, userId: UUID): Unit
}

class MemStateClientRegistry extends ClientRegistry with ClientNotifier {

  private val connections = new AtomicReference[Map[UUID, (UUID, MessageDispatcher)]](Map.empty)

  def add(userId: UUID, connection: MessageDispatcher): UUID = {
    val connectionId = UUID.randomUUID()
    connections.set(connections.get() + (userId -> (connectionId, connection)))
    connectionId
  }

  def remove(userId: UUID, connectionId: UUID): Unit = {
    connections.get().get(userId).filter(_._1 == connectionId).foreach { _ =>
      connections.updateAndGet(_ - userId)
    }
  }

  def sendTo(message: ServerNotification, userId: UUID): Unit = connections.get().get(userId).foreach(_._2.apply(ServerNotification.toJson(message)))

  def connectedClients: Int = connections.get().size
}