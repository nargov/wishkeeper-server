package co.wishkeeper.server.messaging

import co.wishkeeper.server.{Error, GeneralError}

import scala.concurrent.{ExecutionContext, Future}

class EmailSender(emailProvider: EmailProvider, templateEngineAdapter: TemplateEngineAdapter)(implicit ex: ExecutionContext) {
  def sendVerificationEmail(to: String, token: String, firstName: String): Future[Either[Error, Unit]] = {

    val template: Either[Error, String] = templateEngineAdapter.process(EmailSender.verificationEmailTemplate,
      Map("token" -> token, "firstName" -> firstName)).toEither.left.map(t => GeneralError(t.getMessage))

    template.fold(err => Future.successful(Left(err)),
      emailProvider.sendEmail(to, "Wishkeeper <hello@wishkeeper.co>", EmailSender.verificationEmailSubject, "", _))
  }
}

object EmailSender {
  val verificationEmailTemplate = "/templates/email-confirm.mustache"
  val verificationEmailSubject = "Please confirm your email"
}
