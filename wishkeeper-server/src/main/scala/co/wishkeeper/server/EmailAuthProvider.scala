package co.wishkeeper.server

import co.wishkeeper.server.user.Unauthorized
import com.google.firebase.auth.{FirebaseAuth, FirebaseAuthException}

import scala.util.Try

trait EmailAuthProvider {
  def validate(token: String): Either[Error, EmailAuthData]
}

class FirebaseEmailAuthProvider extends EmailAuthProvider {
  override def validate(token: String): Either[Error, EmailAuthData] = Try {
    val data = FirebaseAuth.getInstance().verifyIdToken(token)
    EmailAuthData(data.getEmail, data.isEmailVerified)
  }.toEither.left.map {
    case e: FirebaseAuthException => Unauthorized(e.getMessage)
    case t => GeneralError(t.getMessage)
  }
}

case class EmailAuthData(email: String, verified: Boolean = false)