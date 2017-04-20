package co.wishkeeper.server

import java.util.UUID

case class UserInfo(userId: UUID, facebookData: Option[FacebookData])

case class FacebookData(id: String)
