package io.wishkeeper.server

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import spray.json.{DefaultJsonProtocol, JsString, JsValue, RootJsonFormat, deserializationError}

import scala.concurrent.ExecutionContextExecutor

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {

  implicit object UuidJsonFormat extends RootJsonFormat[UUID] {
    def write(x: UUID) = JsString(x.toString)

    def read(value: JsValue) = value match {
      case JsString(x) => UUID.fromString(x)
      case x => deserializationError("Expected UUID as JsString, but got " + x)
    }
  }

  implicit val wishProtocol: RootJsonFormat[Wish] = jsonFormat1(Wish)
  implicit val newWishRequestProtocol: RootJsonFormat[NewWishRequest] = jsonFormat2(NewWishRequest)
}


object Server extends JsonSupport {

  implicit val system = ActorSystem("server-system")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher


  val route: Route =
    path("wishes") {
      post {
        entity(as[NewWishRequest]) { req =>
          complete(HttpResponse(StatusCodes.Created))
        }
      } ~
      get {
        complete("ok")
      }
    }

  def main(args: String*): Unit = {
    Http().bindAndHandle(route, "localhost", 12300)
  }

}

case class Wish(name: String)

case class NewWishRequest(wish: Wish, userId: UUID)
