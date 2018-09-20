package co.wishkeeper.server.user

import io.circe.{Decoder, Encoder, HCursor, Json}

sealed trait Gender

object Gender {

  case object Female extends Gender

  case object Male extends Gender

  case object Custom extends Gender

  implicit val encoder: Encoder[Gender] = {
    case Female => Json.fromString("female")
    case Male => Json.fromString("male")
    case _ => Json.fromString("custom")
  }

  implicit val decoder: Decoder[Gender] = (c: HCursor) => c.as[String].map {
    case "female" => Female
    case "male" => Male
    case "custom" => Custom
  }
}

sealed trait GenderPronoun

object GenderPronoun {

  case object Female extends GenderPronoun

  case object Male extends GenderPronoun

  case object Neutral extends GenderPronoun

  implicit val encoder: Encoder[GenderPronoun] = {
    case Female => Json.fromString("female")
    case Male => Json.fromString("male")
    case Neutral => Json.fromString("neutral")
  }

  implicit val decoder: Decoder[GenderPronoun] = (c: HCursor) => c.as[String].map {
    case "female" => Female
    case "male" => Male
    case "neutral" => Neutral
  }
}

