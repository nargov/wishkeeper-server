package co.wishkeeper.server

import io.circe.generic.extras.auto._
import io.circe.generic.extras.Configuration
import io.circe.parser.decode
import io.circe.syntax._
import org.specs2.mutable.Specification

class CirceLearningTest extends Specification {

  implicit val circeConfig = Configuration.default.withDefaults

  "decode missing values with defaults" >> {

    println(Foo(baz = 6).asJson.spaces2)
    decode[Foo]("""{"baz":6}""") must beRight(beEqualTo(Foo(baz = 6)))
  }
}

case class Foo(bar: String = "bar", baz: Int)
