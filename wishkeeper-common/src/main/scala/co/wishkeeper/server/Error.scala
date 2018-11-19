package co.wishkeeper.server

trait Error {
  val message: String
  val code: String
}

case class GeneralError(message: String) extends Error {
  override val code: String = "general"
}

case object DbErrorEventsNotSaved extends Error {
  override val message: String = "Error saving events to database"
  override val code: String = "general.events.save"
}

case class DatabaseSaveError(message: String) extends Error {
  override val code: String = "error.db.save"
}

case class DatabaseError(message: String, ex: Throwable) extends Error {
  override val code: String = "error.db.general"
}