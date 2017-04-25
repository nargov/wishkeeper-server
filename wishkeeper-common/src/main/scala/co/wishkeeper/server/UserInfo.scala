package co.wishkeeper.server

import java.util.UUID

case class UserInfo(userId: UUID, facebookData: Option[FacebookData] = None)

case class FacebookData(id: String)
