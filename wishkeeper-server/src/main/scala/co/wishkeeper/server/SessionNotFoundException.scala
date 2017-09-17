package co.wishkeeper.server

import java.util.UUID

class SessionNotFoundException(sessionId: Option[UUID]) extends RuntimeException(
  sessionId.map(id => s"Session ${id.toString} not found.").getOrElse("Session not found"))
