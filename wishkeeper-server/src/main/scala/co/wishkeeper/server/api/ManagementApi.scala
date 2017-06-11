package co.wishkeeper.server.api

import java.util.UUID

import co.wishkeeper.server.{UserProfile, Wish}

trait ManagementApi {
  def userIdFor(facebookId: String): Option[UUID]

  def profileFor(userId: UUID): UserProfile

  def wishesFor(userId: UUID): List[Wish]
}
