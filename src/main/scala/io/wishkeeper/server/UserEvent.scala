package io.wishkeeper.server

import java.util.UUID

sealed trait UserEvent

case class UserCreated(userId: UUID) extends UserEvent

case class WishCreated(userId: UUID, id: UUID, name: String) extends UserEvent


