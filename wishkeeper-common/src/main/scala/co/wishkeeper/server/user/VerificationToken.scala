package co.wishkeeper.server.user

import java.util.UUID

import org.joda.time.DateTime

case class VerificationToken(token: UUID, email: String, userId: UUID, created: DateTime = DateTime.now(), verified: Boolean = false)
