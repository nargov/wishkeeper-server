package io.wishkeeper.server

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.datastax.driver.core.{Cluster, Session}
import com.whisk.docker.impl.spotify.DockerKitSpotify
import com.whisk.docker.scalatest.DockerTestKit
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.circe.generic.auto._
import io.circe.syntax._
import io.wishkeeper.server.Commands.AddWish
import org.scalatest.{EitherValues, FlatSpec, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class ServerIT extends FlatSpec with DockerTestKit with DockerKitSpotify with CassandraDocker with Matchers with EitherValues {

  val cluster = Cluster.builder().addContactPoint("localhost").build()
  var session: Session = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    session = cluster.connect()

    session.execute("create keyspace if not exists wishkeeper with replication = {'class': 'SimpleStrategy', 'replication_factor': 1}").wasApplied() shouldBe true

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

//    session.execute(
//      """
//        |create table if not exists wishkeeper.user_wishlist(
//        | userId UUID,
//        |
//      """.stripMargin) //TODO Finish this view model table
  }

  implicit val system = ActorSystem("test-system")
  implicit val materializer = ActorMaterializer()

  it should "return the list of saved wishes" in {
    Server.main()

    val wish = Wish("wish-name")
    val wishJson = AddWish(UUID.randomUUID(), wish).asJson.toString
    val creationResponse: Future[HttpResponse] = Http().singleRequest(HttpRequest(HttpMethods.POST, "http://localhost:12300/wishes", entity = HttpEntity(ContentTypes.`application/json`, wishJson)))
    val res = Await.result(creationResponse, 5 seconds)
    res.status shouldEqual StatusCodes.Created

    val getResponse: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = "http://localhost:12300/wishes"))
    val futureWishes = getResponse.flatMap(res => Unmarshal(res.entity).to[Array[Wish]])
    val wishes = Await.result(futureWishes, 5 seconds)
    wishes should have size 1
  }
}
