package io.wishkeeper.server

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import org.scalatest.AsyncFlatSpec
import spray.json._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps

class ServerIT extends AsyncFlatSpec with JsonSupport {

  implicit val system = ActorSystem("test-system")
  implicit val materializer = ActorMaterializer()

  it should "return the list of saved wishes" in {
    val server = Server.main()

    val wishJson = NewWishRequest(Wish("wish-name"), UUID.randomUUID()).toJson.toString
    val creationResponse: Future[HttpResponse] = Http().singleRequest(HttpRequest(HttpMethods.POST, "http://localhost:12300/wishes", entity = HttpEntity(ContentTypes.`application/json`, wishJson)))
    val res = Await.result(creationResponse, 5 seconds)
    assert(res.status == StatusCodes.Created)

    val getResponse = Http().singleRequest(HttpRequest(uri = "http://localhost:12300/wishes"))
    getResponse.map(res => {
      val wishes = JsonParser(res.entity.toString).convertTo[List[Wish]]
      assert(wishes.size == 1)
    })
  }
}
