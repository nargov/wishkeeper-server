package co.wishkeeper

import co.wishkeeper.server.user.{Platform, ValidationError}
import io.circe.Decoder.Result
import io.circe._
import org.joda.time.DateTime

package object json {
  implicit val dateTimeFormat: Encoder[DateTime] with Decoder[DateTime] = new Encoder[DateTime] with Decoder[DateTime] {
    override def apply(a: DateTime): Json = Encoder.encodeLong(a.toDate.getTime)

    override def apply(c: HCursor): Result[DateTime] = Decoder.decodeLong.map(new DateTime(_)).apply(c)
  }

  implicit val validationErrorEncoder: Encoder[ValidationError] = Encoder.encodeJsonObject.contramap[ValidationError](err =>
    JsonObject("message" -> Json.fromString(err.message), "code" -> Json.fromString(err.code)))

  implicit val platformFormat: Encoder[Platform] with Decoder[Platform] = new Encoder[Platform] with Decoder[Platform] {
    override def apply(a: Platform): Json = Encoder.encodeString(a.toString.toLowerCase())

    override def apply(c: HCursor): Result[Platform] = Decoder.decodeString.map {
      case "ios" => Platform.iOS
      case _ => Platform.Android
    }.apply(c)
  }
}
