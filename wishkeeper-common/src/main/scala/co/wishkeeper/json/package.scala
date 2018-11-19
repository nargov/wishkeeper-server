package co.wishkeeper

import co.wishkeeper.server.user.ValidationError
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
}
