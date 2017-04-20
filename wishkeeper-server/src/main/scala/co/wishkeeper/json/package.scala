package co.wishkeeper

import io.circe.Decoder.Result
import io.circe.{Decoder, Encoder, HCursor, Json}
import org.joda.time.DateTime

package object json {
  implicit val dateTimeFormat: Encoder[DateTime] with Decoder[DateTime] = new Encoder[DateTime] with Decoder[DateTime] {
    override def apply(a: DateTime): Json = Encoder.encodeLong(a.toDate.getTime)

    override def apply(c: HCursor): Result[DateTime] = Decoder.decodeLong.map(new DateTime(_)).apply(c)
  }
}
