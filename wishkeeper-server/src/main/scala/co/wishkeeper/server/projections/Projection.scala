package co.wishkeeper.server.projections

trait Projection {
  def rebuild(): Unit
}
