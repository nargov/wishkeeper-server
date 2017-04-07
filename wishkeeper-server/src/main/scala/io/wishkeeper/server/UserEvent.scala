package io.wishkeeper.server

import java.util.UUID

import org.joda.time.DateTime

object Events {

  sealed trait UserEvent

  case class UserConnected(userId: UUID, time: DateTime) extends UserEvent

  case class WishCreated(userId: UUID, id: UUID) extends UserEvent

  case class WishNameSet(wishId: UUID, name: String) extends UserEvent

}

object Commands {

  case class AddWish(userId: UUID, wish: Wish)

  case class ConnectUser()

}