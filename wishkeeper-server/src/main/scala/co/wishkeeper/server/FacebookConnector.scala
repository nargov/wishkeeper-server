package co.wishkeeper.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, StatusCodes}
import akka.stream.ActorMaterializer
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import io.circe.parser._
import org.joda.time.DateTime

import scala.concurrent.{ExecutionContext, Future}

trait FacebookConnector {
  def isValid(token: String): Future[Boolean]

  def friendsFor(facebookId: String, accessToken: String): Future[List[FacebookFriend]]
}

object FacebookConnector {
  val apiVersion = "v2.9"
}

class AkkaHttpFacebookConnector(appId: String, appSecret: String)
                               (implicit actorSystem: ActorSystem, ec: ExecutionContext, am: ActorMaterializer) extends FacebookConnector {

  implicit val circeConfig = Configuration.default.withDefaults.withSnakeCaseMemberNames

  override def isValid(token: String): Future[Boolean] = {
    val eventualResponse = Http().singleRequest(HttpRequest().
      withUri(s"https://graph.facebook.com/debug_token?input_token=$token&access_token=$appId|$appSecret"))
    val eventualResult = eventualResponse.
      flatMap(_.entity.dataBytes.runFold("")(_ + _.utf8String)).
      map(res => decode[FacebookDebugTokenResult](res))
    eventualResult.map {
      case Right(result) => result.data.isValid
      case Left(err) =>
        //TODO report error?
        false
    }
  }

  private def friendsFor(url: String): Future[List[FacebookFriend]] = {
    println("request " + DateTime.now().toString)
    val response = Http().singleRequest(HttpRequest().withUri(url))

    val jsonStr = response.flatMap {
      case res if res.status == StatusCodes.OK => res.entity.dataBytes.runFold("")(_ + _.utf8String)
    }

    val eventualResult = jsonStr.map(decode[FacebookFriendsResult]).map {
      case Right(result) =>
        println("response " + DateTime.now().toString)
        result
      case Left(err) => throw err
    }

    eventualResult.flatMap { result =>
      result.paging.map { paging =>
        paging.next.map { next =>
          friendsFor(next).map(_ ++ result.data)
        }.getOrElse(Future.successful(result.data))
      }.getOrElse(Future.successful(result.data))
    }
  }

  override def friendsFor(facebookId: String, accessToken: String): Future[List[FacebookFriend]] = {
    friendsFor(s"https://graph.facebook.com/${FacebookConnector.apiVersion}/$facebookId/friends?access_token=$accessToken")
  }
}

case class FacebookFriendsResult(data: List[FacebookFriend], paging: Option[FacebookPaging], summary: FacebookSummary)

case class FacebookFriend(name: String, id: String)

case class FacebookPaging(cursors: FacebookCursors, previous: Option[String], next: Option[String])

case class FacebookCursors(before: String, after: String)

case class FacebookSummary(total_count: Int)

case class FacebookDebugTokenResult(data: FacebookTokenData)

case class FacebookTokenData(appId: String, application: String, expiresAt: Long, isValid: Boolean, scopes: List[String], userId: Long)