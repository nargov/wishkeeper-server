package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.user.commands.SetFacebookUserInfo
import co.wishkeeper.server.Events._
import co.wishkeeper.server.UserTestHelper._
import org.scalatest.{FlatSpec, Matchers}

class SetFacebookUserInfoTest extends FlatSpec with Matchers {

  it should "successfully parse a full date" in {
    val birthday = "01/02/1980"
    shouldParseSuccessfully(birthday)
  }

  it should "successfully parse a month/day date" in {
    val birthday = "07/15"
    shouldParseSuccessfully(birthday)
  }

  it should "fail to parse random string" in {
    val birthday = "r9gys53"
    shouldFailToParse(birthday)
  }

  it should "fail to parse year only" in {
    val birthday = "1987"
    shouldFailToParse(birthday)
  }

  it should "fail to parse date with partial year" in {
    val birthday = "01/02/80"
    shouldFailToParse(birthday)
  }

  val minAge = Some(20)
  val birthday = "06/12"
  val email = "abc@xyz.com"
  it should "create events for properties" in {
    val firstName = "James"
    val lastName = "McGuill"
    val gender = "Male"
    val locale = "en_US"
    val name = firstName + " " + lastName
    val timezone = -5
    SetFacebookUserInfo(
      Option(AgeRange(minAge, None)),
      Option(birthday),
      Option(email),
      Option(firstName),
      Option(lastName),
      Option(name),
      Option(gender),
      Option(locale),
      Option(timezone)
    ).process(User(userId)) should contain theSameElementsAs List(
      UserAgeRangeSet(userId, minAge, None),
      UserBirthdaySet(userId, birthday),
      UserEmailSet(userId, email),
      UserFirstNameSet(userId, firstName),
      UserLastNameSet(userId, lastName),
      UserNameSet(userId, name),
      UserGenderSet(userId, gender),
      UserLocaleSet(userId, locale),
      UserTimeZoneSet(userId, timezone))
  }

  it should "create events only for given properties" in {
    SetFacebookUserInfo(
      Option(AgeRange(minAge, None)),
      Option(birthday),
      Option(email),
      None, None, None, None, None, None
    ).process(User(userId)) should contain theSameElementsAs List(
      UserAgeRangeSet(userId, minAge, None),
      UserBirthdaySet(userId, birthday),
      UserEmailSet(userId, email)
    )
  }

  it should "create an event for user photo" in {
    val pictureLink = "http://my.user.photo"
    SetFacebookUserInfo(picture = Some(pictureLink)).process(User(userId)) should
      contain theSameElementsAs List(UserPictureSet(userId, pictureLink))
  }

  it should "create events only for data missing from the user account" in {
    val existingEvents = EventsTestHelper.asEventInstants(List(
      UserNameSet(userId, "name"),
      UserFirstNameSet(userId, "first name"),
      UserLastNameSet(userId, "last name"),
      UserEmailSet(userId, "email"),
      UserBirthdaySet(userId, "11/11/2011"),
      UserAgeRangeSet(userId, Option(20), Option(30)),
      UserGenderSet(userId, "Male"),
      UserLocaleSet(userId, "US"),
      UserTimeZoneSet(userId, -7)
    ))

    val firstName = "James"
    val lastName = "McGuill"
    val gender = "Male"
    val locale = "en_US"
    val name = firstName + " " + lastName
    val timezone = -5
    SetFacebookUserInfo(
      Option(AgeRange(minAge, None)),
      Option(birthday),
      Option(email),
      Option(firstName),
      Option(lastName),
      Option(name),
      Option(gender),
      Option(locale),
      Option(timezone)
    ).process(existingEvents.foldLeft(aUser)(_.applyEvent(_))) should be(empty)
  }

  val userId = UUID.randomUUID()

  private def shouldParseSuccessfully(birthday: String) = {
    SetFacebookUserInfo.getValidUserBirthdayEvent(userId, birthday) shouldBe Some(UserBirthdaySet(userId, birthday))
  }

  private def shouldFailToParse(birthday: String) = {
    SetFacebookUserInfo.getValidUserBirthdayEvent(userId, birthday) shouldBe None
  }
}
