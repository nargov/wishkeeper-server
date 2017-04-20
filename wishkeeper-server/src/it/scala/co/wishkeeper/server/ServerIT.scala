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
import org.scalatest._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class ServerIT extends AsyncFlatSpec with Matchers with EitherValues with BeforeAndAfterAll {

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
        | seqMax bigint STATIC,
        | time timestamp,
        | event blob,
        | PRIMARY KEY((userId), seq)
        |)
      """.stripMargin).wasApplied() shouldBe true

    session.execute(
      """
        |create table if not exists wishkeeper.user_info_by_facebook_id(
        | facebookId text,
        | seq bigint,
        | userInfo blob,
        | PRIMARY KEY(facebookId)
        |)
      """.stripMargin
    )
  }

  override def afterAll(): Unit = {
    session.close()
    cluster.close()
    system.terminate()
  }

  implicit val system = ActorSystem("test-system")
  implicit val materializer = ActorMaterializer()

}
