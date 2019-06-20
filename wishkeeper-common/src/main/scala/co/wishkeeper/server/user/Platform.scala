package co.wishkeeper.server.user

sealed trait Platform

object Platform {

  case object iOS extends Platform

  case object Android extends Platform

}
