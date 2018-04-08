package co.wishkeeper.server

trait Error {
  val message: String
}

case class GeneralError(message: String) extends Error

case object DbErrorEventsNotSaved extends Error {
  override val message: String = "Error saving events to database"
}