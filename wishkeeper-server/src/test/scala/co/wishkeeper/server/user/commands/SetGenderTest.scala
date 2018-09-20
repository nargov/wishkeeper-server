package co.wishkeeper.server.user.commands

import co.wishkeeper.server.Events.UserGenderSet2
import co.wishkeeper.server.UserTestHelper.aUser
import co.wishkeeper.server.user.{Gender, GenderPronoun}
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import io.circe.parser._
import io.circe.syntax._
import org.specs2.mutable.Specification



class SetGenderTest extends Specification {
  implicit val config = Configuration.default.withDefaults

  "Encode and decode json" >> {
    val command = SetGender(Gender.Female, None, Option(GenderPronoun.Female))
    val json = command.asJson.spaces2

    decode[SetGender](json).right.get must beEqualTo(command)
  }

  "decode json from string enum" >> {
    val command = SetGender(Gender.Female, None, Option(GenderPronoun.Female))
    val json = """{"gender": "female", "genderPronoun": "female"}"""
    decode[SetGender](json).right.get must beEqualTo(command)
  }

  "Creates events" >> {
    SetGender(Gender.Custom, Option("Non Binary"), Option(GenderPronoun.Neutral)).process(aUser) must contain(
      UserGenderSet2(Gender.Custom, Option("Non Binary"), Option(GenderPronoun.Neutral)))
  }
}
