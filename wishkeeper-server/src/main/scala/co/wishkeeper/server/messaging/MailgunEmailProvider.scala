package co.wishkeeper.server.messaging

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpCharsets.`UTF-8`
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.stream.ActorMaterializer
import co.wishkeeper.server
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

trait EmailProvider {
  def sendEmail(to: String, from: String, subject: String, text: String, html: String): Future[Either[server.Error, Unit]]
}

class MailgunEmailProvider(apiKey: String)(implicit sys: ActorSystem, am: ActorMaterializer, ec: ExecutionContext) extends EmailProvider {
  private val url = "https://api.mailgun.net/v3/mg.wishkeeper.co/messages"

  private val log = LoggerFactory.getLogger(getClass.getName)
  log.info("Mailgun Email Provider started with key " + apiKey)

  def sendEmail(to: String, from: String, subject: String, text: String, html: String): Future[Either[server.Error, Unit]] = {
    Http().singleRequest(HttpRequest()
      .withMethod(HttpMethods.POST)
      .withUri(url).withEntity(FormData(Map(
      "from" -> from,
      "to" -> to,
      "subject" -> subject,
      "text" -> text,
      "html" -> html
    )).toEntity(`UTF-8`)).addCredentials(BasicHttpCredentials("api", apiKey))
    ).map(res => {
      println("Response from mailgun: " + res)
      Either.cond(res.status.isSuccess(), (), MailgunError(res.status.reason()))
    }).recover {
      case t: Exception =>
        log.error("error sending mail", t)
        Right(MailgunError(t.getMessage, Option(t)))
    }
  }
}

case class MailgunError(message: String, ex: Option[Exception] = None) extends server.Error {
  override val code: String = "mailgun.err"
}