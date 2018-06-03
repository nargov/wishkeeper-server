package co.wishkeeper.server

import akka.NotUsed
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.testkit.{Specs2RouteTest, WSProbe}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source, SourceQueueWithComplete}
import org.specs2.mutable.Specification

class AkkaWebsocketsLearningTest extends Specification with Specs2RouteTest {
  "Akka Websockets" should {
    "send messages" in {

      var connections: Map[String, SourceQueueWithComplete[Message]] = Map.empty

      val in: Sink[Message, NotUsed] = Flow[Message].to(Sink.foreach {
        case TextMessage.Strict(msg) => println(msg)
        case _ =>
      })

      val out: Source[Message, SourceQueueWithComplete[Message]] = Source.queue(10, OverflowStrategy.fail)
      def handler(user: String): Flow[Message, Message, Any] = Flow.fromSinkAndSourceMat(in, out)((_, outbound) => {
        outbound.watchCompletion().foreach(_ => {
          println("At completion")
          connections -= user
        })
        connections += (user -> outbound)
      })

      val route = {
        get {
          pathSingleSlash {
            parameter("userid") { userId =>
              handleWebSocketMessages(handler(userId))
            }
          }
        }
      }

      def sendMessageTo(user: String, message: String) = connections(user).offer(TextMessage.Strict(message))

      val client = WSProbe()

      WS("/?userid=user1", client.flow) ~> route ~> check {
        isWebSocketUpgrade must beTrue
        connections must haveKey("user1")

        client.sendMessage("Hello world")

        val expectedMessage = "Hi There"
        sendMessageTo("user1", expectedMessage)

        client.expectMessage(expectedMessage)

        ok
      }
    }
  }
}