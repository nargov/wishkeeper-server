package co.wishkeeper.server

import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

import co.wishkeeper.server.Commands.UserCommand
import co.wishkeeper.server.Events.{NoOp, UserEvent}
import com.wixpress.common.specs2.JMock
import org.specs2.matcher.MatcherMacros
import org.specs2.mutable.Specification

import scala.language.experimental.macros

class UserTest extends Specification with MatcherMacros with JMock {
  "User" should {
    "create a new user" in {
      User.createNew() must matchA[User].id(beAnInstanceOf[UUID])
    }

    "create a random id for a new user" in {
      User.createNew().id must not(beEqualTo(User.createNew().id))
    }

    "process command" in {
      val user = User.createNew()

      val command = mock[UserCommand]

      checking {
        oneOf(command).process(user)
      }

      user.processCommand(command)
    }

    "apply event" in {
      implicit object NoOpEventHandler extends EventHandler[NoOp.type] {
        val called = new AtomicBoolean(false)
        override def apply(user: User): User = {
          called.set(true)
          user
        }
      }

      User.createNew().applyEvent(NoOp)
      NoOpEventHandler.called.get() must beTrue
    }

  }
}
