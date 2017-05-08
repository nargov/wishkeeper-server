package co.wishkeeper.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, StatusCodes}
import akka.stream.ActorMaterializer
import io.circe.generic.auto._
import io.circe.parser._

import scala.concurrent.{ExecutionContext, Future}

trait FacebookConnector {
  def friendsFor(facebookId: String, accessToken: String): Future[List[FacebookFriend]]
}
object FacebookConnector {
  val apiVersion = "v2.9"
}

class AkkaHttpFacebookConnector(implicit actorSystem: ActorSystem, ec: ExecutionContext, am: ActorMaterializer) extends FacebookConnector {

  override def friendsFor(facebookId: String, accessToken: String): Future[List[FacebookFriend]] = {
    val response = Http().singleRequest(HttpRequest().
      withUri(s"https://graph.facebook.com/${FacebookConnector.apiVersion}/$facebookId/friends?access_token=$accessToken"))

    val jsonStr = response.flatMap {
      case res if res.status == StatusCodes.OK => res.entity.dataBytes.runFold("")(_ + _.utf8String)
    }

    val friends = jsonStr.map(decode[FacebookFriendsResult])

    friends.flatMap {
      case Right(result) => Future.successful(result.data)
      case Left(err) => Future.failed(err)
    }
  }
}

case class FacebookFriendsResult(data: List[FacebookFriend], paging: Option[FacebookPaging], summary: FacebookSummary)
case class FacebookFriend(name: String, id: String)
case class FacebookPaging(cursors: FacebookCursors, previous: Option[String], next: Option[String])
case class FacebookCursors(before: String, after: String)
case class FacebookSummary(total_count: Int)
