package co.wishkeeper.server.messaging

import co.wishkeeper.server.{Error, GeneralError}

import scala.concurrent.{ExecutionContext, Future}

class EmailSender(emailProvider: EmailProvider, templateEngineAdapter: TemplateEngineAdapter)(implicit ex: ExecutionContext) {
  def sendVerificationEmail(to: String, from: String, subject: String, token: String, firstName: String): Future[Either[Error, Unit]] = {

    println("Preparing verification email")
    val template: Either[Error, String] = templateEngineAdapter.process(EmailSender.verificationEmailTemplate,
      Map("token" -> token, "firstName" -> firstName)).toEither.left.map(t => GeneralError(t.getMessage))

    println(template)
    template.fold(err => Future.successful(Left(err)), emailProvider.sendEmail(to, from, subject, "", _))
  }
}

object EmailSender {
  val verificationEmailTemplate = "/templates/email-confirm.mustache"
  val verificationEmailSubject = "Please confirm your email"
}
