package co.wishkeeper.server.messaging

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

import com.wixpress.common.specs2.JMock
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class MemStateClientRegistryTest extends Specification with JMock {
  type MessageDispatcher = String => Unit

  "ClientRegistry" should {
    "Send message to registered clients" in new Context {
      registry.add(userId, dispatcherSpy)

      checking {
        oneOf(dispatcherSpy).apply(expectedMessage)
      }

      sendMessageToUser()
    }

    "Remove registered connection" in new Context {
      val connectionId = registry.add(userId, dispatcherSpy)
      registry.remove(userId, connectionId)

      checking {
        never(dispatcherSpy).apply(having(any))
      }

      sendMessageToUser()
    }

    "Return the number of connected clients" in new Context {
      registry.add(userId, dispatcherSpy)
      registry.connectedClients must beEqualTo(1)
    }

    "Not remove connection if id mismatch" in new Context {
      val oldConnectionId = registry.add(userId, _ => {})
      registry.add(userId, dispatcherSpy)

      registry.remove(userId, oldConnectionId)

      checking {
        oneOf(dispatcherSpy).apply(expectedMessage)
      }

      sendMessageToUser()
    }

    "Decrement number of connected clients on remove" in new Context {
      registry.remove(userId, registry.add(userId, dispatcherSpy))
      registry.connectedClients must beEqualTo(0)
    }

    "notify listeners when user connection is added" in new Context {
      val called = new AtomicReference[ClientRegistryEvent]()
      registry.addListener(called.set)

      registry.add(userId, _ => ())

      called.get() must beEqualTo(UserConnectionAdded(userId))
    }

    "notify listeners when user connection is removed" in new Context {
      val called = new AtomicReference[ClientRegistryEvent]()
      val connectionId: UUID = registry.add(userId, _ => ())
      registry.addListener(called.set)

      registry.remove(userId, connectionId)

      called.get() must beEqualTo(UserConnectionRemoved(userId))
    }
  }

  trait Context extends Scope {
    val expectedMessage = ServerNotification.toJson(NotificationsUpdated)
    val dispatcherSpy = mock[MessageDispatcher]
    val registry = new MemStateClientRegistry
    val userId = UUID.randomUUID()

    def sendMessageToUser() = registry.sendTo(NotificationsUpdated, userId)
  }

}