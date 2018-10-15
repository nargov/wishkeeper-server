package co.wishkeeper.server

import co.wishkeeper.server.user.{Gender, GenderPronoun}
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson.JacksonFactory
import com.google.api.services.people.v1.PeopleService
import org.joda.time.LocalDate
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.util.Try

case class GoogleUser(id: String, email: Option[String], emailVerified: Option[Boolean], name: Option[String], photo: Option[String],
                      locale: Option[String], firstName: Option[String], lastName: Option[String])

trait GoogleAuthAdapter {
  def validateIdToken(token: String): Either[Error, GoogleUser]

  def fetchAdditionalUserData(accessToken: String, googleUserId: String): Either[Error, GoogleUserData]

  def userContactEmails(accessToken: String): Either[Error, List[String]]
}

class SdkGoogleAuthAdapter extends GoogleAuthAdapter {
  private val log = LoggerFactory.getLogger(getClass)
  private val transport = GoogleNetHttpTransport.newTrustedTransport()
  private val jsonFactory = new JacksonFactory()
  private val peopleService = new PeopleService.Builder(transport, jsonFactory, _ => {}).setApplicationName("Wishkeeper").build()

  override def validateIdToken(token: String): Either[Error, GoogleUser] = {
    val verifier = new GoogleIdTokenVerifier.Builder(transport, jsonFactory).build()
    val googleIdToken = Try(verifier.verify(token))
    googleIdToken.toEither.map { idToken =>
      val payload = idToken.getPayload
      GoogleUser(
        payload.getSubject,
        Option(payload.getEmail),
        Option(payload.getEmailVerified),
        Option(payload.get("name").asInstanceOf[String]),
        Option(payload.get("picture").asInstanceOf[String]),
        Option(payload.get("locale").asInstanceOf[String]),
        Option(payload.get("given_name").asInstanceOf[String]),
        Option(payload.get("family_name").asInstanceOf[String]))
    }.left.map { t =>
      log.error("Error verifying google id token", t)
      GeneralError(t.getMessage)
    }
  }

  override def fetchAdditionalUserData(accessToken: String, googleUserId: String): Either[Error, GoogleUserData] = {
    Try {
      val resourceId = s"people/$googleUserId"
      val person = peopleService.people().get(resourceId)
        .setAccessToken(accessToken)
        .setPersonFields("genders,birthdays")
        .execute()
      val birthDate = Option(person.getBirthdays).flatMap { birthdays =>
        if (birthdays.size() > 0) Option(birthdays.get(0)).map(_.getDate) else None
      }
      val birthday = birthDate.map(d => new LocalDate(d.getYear, d.getMonth, d.getDay))
      val gender: Option[String] = Option(person.getGenders).flatMap { genders =>
        if (genders.size() > 0) Option(genders.get(0)).map(_.getValue) else None
      }
      val genderData = gender.map {
        case g if g == "male" => GenderData(Gender.Male)
        case g if g == "female" => GenderData(Gender.Female)
        case g => GenderData(Gender.Custom, Option(g), Option(GenderPronoun.Neutral))
      }
      GoogleUserData(birthday, genderData)
    }.toEither.left.map { t =>
      log.error("Error getting additional google user data", t)
      GeneralError(t.getMessage)
    }
  }

  override def userContactEmails(accessToken: String): Either[Error, List[String]] = {
    Try {
      val maybeContacts = Option(peopleService.people().connections().list("people/me")
        .setAccessToken(accessToken)
        .setPersonFields("emailAddresses")
        .setPageSize(1000)
        .execute())

      maybeContacts.flatMap(c => Option(c.getConnections)).map { connections =>
        connections.asScala.filter(person => {
          val emails = person.getEmailAddresses
          emails != null && emails.size() > 0 && emails.asScala.exists(_.getMetadata.getPrimary)
        }).map(_.getEmailAddresses.asScala.filter(_.getMetadata.getPrimary).head.getValue).toList
      }.getOrElse(Nil)
    }.toEither.left.map { t =>
      log.error("Error getting contacts for google account", t)
      GeneralError(t.getMessage)
    }
  }
}

case class GoogleAuthError(message: String) extends Error

case class GoogleUserData(birthday: Option[LocalDate] = None, gender: Option[GenderData] = None)