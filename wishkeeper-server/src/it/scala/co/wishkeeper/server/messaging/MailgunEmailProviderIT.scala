package co.wishkeeper.server.messaging

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import co.wishkeeper.server
import com.typesafe.config.ConfigFactory
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification

import scala.concurrent.Future
import scala.concurrent.duration._

class MailgunEmailProviderIT(implicit ee: ExecutionEnv) extends Specification {
  implicit val system = ActorSystem("test")
  implicit val materializer = ActorMaterializer()
  val conf = ConfigFactory.load()

  "Should send email" >> {
    val result: Future[Either[server.Error, Unit]] = new MailgunEmailProvider(conf.getString("wishkeeper.mail.mailgun.api.key")).sendEmail(
      to = "nimrod@wishkeeper.co",
      from = "do-not-reply@wishkeeper.co",
      subject = "Test Email",
      text = "This is a test email",
      html =
        """<html>
          |<head>
          | <style>
          |   body {background-color: #eeeeee}
          |   h1 {display: block; width: 100%; height: 40; background-color: yellow}
          | </style>
          |</head>
          |<body>
          | <h1>Test</h1>
          |</body>
          |</html>""".stripMargin)
    result must beRight[Unit].await(20, 500.millis)
  }
}
