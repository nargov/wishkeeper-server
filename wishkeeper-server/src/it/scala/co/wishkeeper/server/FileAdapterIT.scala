package co.wishkeeper.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.stream.ActorMaterializer
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.{After, Specification}
import org.specs2.specification.Scope

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class FileAdapterIT(implicit ee: ExecutionEnv) extends Specification {
  "Should return an InputStream to the given url" in new Scope with After {
    implicit val system = ActorSystem("test-system")
    implicit val am = ActorMaterializer()

    val handler: HttpRequest => HttpResponse =
      _ => HttpResponse(StatusCodes.OK, List(`Content-Type`(ContentTypes.`application/octet-stream`)), HttpEntity(Array[Byte](3)))
    val server: Future[ServerBinding] = Http().bindAndHandleSync(handler, "127.0.0.1", 15500)

    Await.ready(server, 5.seconds)

    val files = new JavaFileAdapter

    files.inputStreamFor("http://127.0.0.1:15500/").foreach { stream =>
      stream.available() must beEqualTo(1)
      stream.read() must beEqualTo(3)
    }

    override def after: Any = {
      server.foreach(_.unbind())
    }
  }
}
