package co.wishkeeper.server.user.commands

import co.wishkeeper.server.Events.{UserFirstNameSet, UserLastNameSet, UserNameSet}
import co.wishkeeper.server.UserTestHelper.aUser
import org.specs2.mutable.Specification

class SetUserNameTest extends Specification {
  "Creates name set events" >> {
    val user = aUser
    val firstName = "first"
    val lastName = "last"
    SetUserName(Option(firstName), Option(lastName)).process(user) must containTheSameElementsAs(List(
      UserFirstNameSet(user.id, firstName),
      UserLastNameSet(user.id, lastName),
      UserNameSet(user.id, s"$firstName $lastName")
    ))
  }

  "Creates names from only last name" >> {
    val user = aUser
    val lastName = "last"
    SetUserName(None, Option(lastName)).process(user) must containTheSameElementsAs(List(
      UserFirstNameSet(user.id, ""),
      UserLastNameSet(user.id, lastName),
      UserNameSet(user.id, s"$lastName")
    ))
  }

  "Creates names from only first name" >> {
    val user = aUser
    val firstName = "first"
    SetUserName(Option(firstName), None).process(user) must containTheSameElementsAs(List(
      UserFirstNameSet(user.id, firstName),
      UserLastNameSet(user.id, ""),
      UserNameSet(user.id, s"$firstName")
    ))
  }
}
