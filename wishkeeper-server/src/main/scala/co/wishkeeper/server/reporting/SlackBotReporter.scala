package co.wishkeeper.server.reporting

import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.stream.ActorMaterializer
import co.wishkeeper.server
import co.wishkeeper.server.notifications.{UserAddedWish, UserFirstConnection, UsersBecameFriends, WishWasReserved}
import io.circe.generic.auto._
import io.circe.syntax._

import scala.concurrent.{ExecutionContext, Future}

trait Reporter {
  def report(payload: Any): Future[Either[server.Error, Unit]]

}

class SlackBotReporter(slackWebHookUri: String, ec: ExecutionContext = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(3)))
                      (implicit sys: ActorSystem, am: ActorMaterializer) extends Reporter {

  implicit val executionContext = ec

  override def report(payload: Any): Future[Either[server.Error, Unit]] = {
    payload match {
      case UserFirstConnection(userId, time, name) => send(s"New User! ${name.getOrElse(s"N/A ($userId)")} connected at $time")
      case UserAddedWish(userId, userName, time, name) =>
        send(s"User ${userName.getOrElse(s"N/A ($userId)")} added a new wish [${name.getOrElse("N/A")}] at $time")
      case WishWasReserved(wishId, wishName, userId, userName, reserverId, reserverName) =>
        send(s"User ${userName.getOrElse(s"N/A ($userId)")}'s Wish ${wishName.getOrElse(s"N/A ($wishId)")} was reserved by " +
          s"${reserverName.getOrElse(s"N/A ($reserverId)")}")
      case UsersBecameFriends(userId, userName, friendId, friendName) =>
        send(s"User ${userName.getOrElse(s"N/A ($userId)")} and User ${friendName.getOrElse(s"N/A ($friendId)")} are now Friends!")
    }
  }

  private def send(msg: String): Future[Either[server.Error, Unit]] = {
    val response: Future[HttpResponse] = Http().singleRequest(HttpRequest(
      method = HttpMethods.POST,
      uri = slackWebHookUri,
      headers = List(`Content-Type`(ContentTypes.`application/json`)),
      entity = HttpEntity(SlackData(msg).asJson.noSpaces)))

    response.transformWith(triedRes => triedRes.fold(t => Future.successful(Left(SlackReportError(msg, t.getMessage))), res =>
      Future.successful(Either.cond(res.status.isSuccess(), (), SlackReportError(msg, res.status.reason())))))
  }
}

object NoOpReporter extends Reporter {
  override def report(payload: Any): Future[Either[server.Error, Unit]] = Future.successful(Right(()))
}

case class SlackData(text: String)

case class SlackReportError(msg: String, reason: String) extends server.Error {
  override val message: String = "Error sending message to slack: " + reason
  override val code: String = "slack.report"
}