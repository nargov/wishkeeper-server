package co.wishkeeper.server.messaging

import org.specs2.mutable.Specification

class ScalateTemplateEngineIT extends Specification {
  "read and populate template" in {
    new ScalateTemplateEngine().process("/templates/test.mustache", Map("key" -> "test")) must beASuccessfulTry(===("This is a test"))
  }
}
