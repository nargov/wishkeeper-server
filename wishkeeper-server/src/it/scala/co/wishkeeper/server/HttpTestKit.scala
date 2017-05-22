package co.wishkeeper.server

import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import io.circe.{Decoder, Encoder}
import io.circe.parser.decode
import io.circe.syntax._
import org.specs2.matcher.{Matcher, MustThrownMatchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

object HttpTestKit {
  implicit val system = ActorSystem("HttpTestKit")
  implicit val materializer = ActorMaterializer()
  private val threadPool = Executors.newCachedThreadPool()
  implicit val executionContext = ExecutionContext.fromExecutor(threadPool)

  implicit val defaultTimeout = Timeout(4.seconds)

  class Get private(uri: String, headers: Map[String, String])(implicit timeout: Timeout) {
    private val response = Http().singleRequest(httpRequest(uri, headers)).map(Response(_, timeout))

    private def waitFor = Await.result(response, timeout.duration)
  }

  object Get {
    def apply(uri: String, headers: Map[String, String] = Map.empty): Response = new Get(uri, headers).waitFor
  }

  class Post[T] private(uri: String, headers: Map[String, String], payload: T)(implicit encoder: Encoder[T], timeout: Timeout) {
    private val response: Future[Response] = Http().singleRequest(
      httpRequest(uri, headers).
        withMethod(HttpMethods.POST).
        withEntity(ContentTypes.`application/json`, payload.asJson.noSpaces)).
      map(Response(_, timeout))

    private def waitFor: Response = Await.result(response, timeout.duration)
  }

  object Post {
    def apply[T](uri: String, payload: T, headers: Map[String, String] = Map.empty)
                (implicit encoder: Encoder[T]): Response = new Post(uri, headers, payload).waitFor

    def async[T](uri: String, payload: T, headers: Map[String, String] = Map.empty)
                (implicit encoder: Encoder[T]): Future[Response] = new Post(uri, headers, payload).response
  }

  private def httpRequest(uri: String, headers: Map[String, String]) =
    HttpRequest().withUri(uri).withHeaders(headers.map(h => RawHeader(h._1, h._2)).toList)


  case class Response private(response: HttpResponse, timeout: Timeout) {
    val status = response.status

    def to[T](implicit decoder: Decoder[T]): T = {
      val futureResult = response.entity.dataBytes.runFold("")(_ + _.utf8String).
        map(json => (json, decode[T](json))).
        map {
          case (_, Right(x)) => x
          case (json, Left(e)) => throw DecodingException(json, e)
        }
      Await.result(futureResult, timeout.duration)
    }
  }

  case class Timeout(duration: Duration)

  case class DecodingException(json: String, e: Throwable) extends RuntimeException(s"Error decoding JSON: $json", e)

}

trait ResponseMatchers extends MustThrownMatchers {
  def beOk: Matcher[HttpTestKit.Response] = beEqualTo(StatusCodes.OK) ^^ {
    (_: HttpTestKit.Response).status
  }
}
