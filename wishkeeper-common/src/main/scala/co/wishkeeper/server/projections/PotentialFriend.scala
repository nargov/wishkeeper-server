package co.wishkeeper.server.projections

import java.util.UUID

case class PotentialFriend(userId: UUID, name: String, image: String, requestSent: Boolean = false)
