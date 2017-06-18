package co.wishkeeper.server

case class UserProfile(socialData: Option[SocialData] = None,
                       ageRange: Option[AgeRange] = None,
                       birthday: Option[String] = None,
                       email: Option[String] = None,
                       firstName: Option[String] = None,
                       lastName: Option[String] = None,
                       name: Option[String] = None,
                       gender: Option[String] = None,
                       locale: Option[String] = None,
                       timezone: Option[Int] = None,
                       picture: Option[String] = None,
                       wishStats: WishStats = WishStats())

case class SocialData(facebookId: Option[String])

case class AgeRange(min: Option[Int], max: Option[Int])

case class WishStats(giftsGiven: Int = 0, giftsReceived: Int = 0)

