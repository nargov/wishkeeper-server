package co.wishkeeper.server.user.commands

import co.wishkeeper.server.Events.{UserEvent, UserFirstNameSet, UserLastNameSet, UserNameSet}
import co.wishkeeper.server.EventsTestHelper.EventsList
import co.wishkeeper.server.User
import co.wishkeeper.server.UserTestHelper._
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class CreateUserEmailFirebaseTest extends Specification {
  "should not return name events when name is already set" in new Context {
    val user = EventsList(emptyUser.id).withFirstName(prevFirstName).withLastName(prevLastName).list.foldLeft(emptyUser)(_.applyEvent(_))
    val events = processCreateUserEmailFirebase(user)

    events must not(contain(anInstanceOf[UserFirstNameSet], anInstanceOf[UserLastNameSet]))
  }

  "should set full name to correct name if both first and last name were missing" in new Context {
    val user = EventsList(emptyUser.id).list.foldLeft(emptyUser)(_.applyEvent(_))
    val events = processCreateUserEmailFirebase(user)

    events must contain[UserEvent](UserNameSet(user.id, newFirstName + " " + newLastName))
  }

  "should set full name to correct name if last name missing" in new Context {
    val user = EventsList(emptyUser.id).withLastName(prevLastName).list.foldLeft(emptyUser)(_.applyEvent(_))
    val events = processCreateUserEmailFirebase(user)

    events must contain[UserEvent](UserNameSet(user.id, newFirstName + " " + prevLastName))
  }

  "should set full name to correct name if first name missing" in new Context {
    val user = EventsList(emptyUser.id).withFirstName(prevFirstName).list.foldLeft(emptyUser)(_.applyEvent(_))
    val events = processCreateUserEmailFirebase(user)

    events must contain[UserEvent](UserNameSet(user.id, prevFirstName + " " + newLastName))
  }

  "should not set full name when both first and last name exist" in new Context {
    val user = EventsList(emptyUser.id).withFirstName(prevFirstName).withLastName(prevLastName).list.foldLeft(emptyUser)(_.applyEvent(_))
    val events = processCreateUserEmailFirebase(user)

    events must not(contain(anInstanceOf[UserNameSet]))
  }

  trait Context extends Scope {
    val prevFirstName = "prevFirstName"
    val prevLastName = "prevLastName"
    val emptyUser = aUser
    val newFirstName = "firstName"
    val newLastName = "lastName"

    def processCreateUserEmailFirebase(user: User) = {
      CreateUserEmailFirebase("email", "idToken", newFirstName, newLastName, "notifId").process(user)
    }
  }
}
