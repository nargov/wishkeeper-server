package co.wishkeeper.server

import co.wishkeeper.server.Events.UserBirthdaySet
import org.scalatest.{FlatSpec, Matchers}

class UserAggregateActorTest extends FlatSpec with Matchers {

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

  private def shouldParseSuccessfully(birthday: String) = {
    UserAggregateActor.getValidUserBirthdayEvent(birthday) shouldBe Some(UserBirthdaySet(birthday))
  }

  private def shouldFailToParse(birthday: String) = {
    UserAggregateActor.getValidUserBirthdayEvent(birthday) shouldBe None
  }
}
