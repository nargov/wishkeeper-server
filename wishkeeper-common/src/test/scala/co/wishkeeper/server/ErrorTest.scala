package co.wishkeeper.server

import co.wishkeeper.json._
import co.wishkeeper.server.user.{EmailNotVerified, ValidationError}
import io.circe.syntax._
import org.specs2.mutable.Specification

class ErrorTest extends Specification {
  "" >> {
    val error: ValidationError = EmailNotVerified("email")
    println(error.asJson.spaces2)
    ok
  }
}
