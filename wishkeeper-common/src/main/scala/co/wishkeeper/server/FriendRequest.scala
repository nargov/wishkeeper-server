package co.wishkeeper.server

import java.util.UUID

import io.circe.{Decoder, Encoder}

case class FriendRequest(id: UUID, userId: UUID, from: UUID)

sealed trait FriendRequestStatus
object FriendRequestStatus {
  case object Pending extends FriendRequestStatus
  case object Approved extends FriendRequestStatus
  case object Ignored extends FriendRequestStatus

  implicit val statusEncoder: Encoder[FriendRequestStatus] = Encoder.encodeString.contramap(_.toString)
  implicit val statusDecoder: Decoder[FriendRequestStatus] = Decoder.decodeString.emap {
    case "Pending" => Right(Pending)
    case "Approved" => Right(Approved)
    case "Ignored" => Right(Ignored)
    case _ => Left("Could not parse FriendRequestStatus $str")
  }
}
