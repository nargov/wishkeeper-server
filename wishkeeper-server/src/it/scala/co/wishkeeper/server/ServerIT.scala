package co.wishkeeper.server

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import co.wishkeeper.server.Commands.ConnectUser
import com.datastax.driver.core.{Cluster, Session}
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.circe.generic.auto._
import io.circe.syntax._
import org.scalatest.{BeforeAndAfterAll, EitherValues, FlatSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.concurrent.ExecutionContext.Implicits.global

class ServerIT extends FlatSpec with Matchers with EitherValues with BeforeAndAfterAll {

  val cluster = Cluster.builder().addContactPoint("localhost").build()
  var session: Session = _

  override def beforeAll(): Unit = {
    CassandraDocker.start()
    session = cluster.connect()

    session.execute("create keyspace if not exists wishkeeper with replication = {'class': 'SimpleStrategy', 'replication_factor': 1}")
      .wasApplied() shouldBe true

    session.execute(
      """
        |create table if not exists wishkeeper.user_events(
        | userId UUID,
        | seq bigint,
        | time timestamp,
        | event blob,
        | PRIMARY KEY((userId), seq)
        |) WITH CLUSTERING ORDER BY (seq DESC)
      """.stripMargin).wasApplied() shouldBe true
  }

  override def afterAll(): Unit = {
    session.close()
    cluster.close()
  }

  implicit val system = ActorSystem("test-system")
  implicit val materializer = ActorMaterializer()

  it should "return an id for a new user connection" in {
    Server.main()

    //    val wish = Wish("wish-name")
    //    val wishJson = AddWish(UUID.randomUUID(), wish).asJson.toString
    //    val creationResponse: Future[HttpResponse] = Http().singleRequest(HttpRequest(HttpMethods.POST, "http://localhost:12300/wishes", entity = HttpEntity(ContentTypes.`application/json`, wishJson)))
    //    val res = Await.result(creationResponse, 5 seconds)
    //    res.status shouldEqual StatusCodes.Created
    //
    //    val getResponse: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = "http://localhost:12300/wishes"))
    //    val futureWishes = getResponse.flatMap(res => Unmarshal(res.entity).to[Array[Wish]])
    //    val wishes = Await.result(futureWishes, 5 seconds)
    //    wishes should have size 1
    val connectEvent = ConnectUser()
    val futureResponse = Http().singleRequest(HttpRequest(
      HttpMethods.POST, "http://localhost:12300/users/connect",
      entity = HttpEntity(ContentTypes.`application/json`, connectEvent.asJson.toString)))
    val response = Await.result(futureResponse, 5 seconds)
    response.status shouldEqual StatusCodes.OK

    Unmarshal(response.entity).to[UUID].map(_ shouldBe a[UUID])
  }
}
