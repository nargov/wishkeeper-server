package co.wishkeeper.server

import java.util.UUID

import co.wishkeeper.server.WishStatus.WishStatus
import org.joda.time.DateTime

case class Wish(id: UUID,
                name: Option[String] = None,
                link: Option[String] = None,
                store: Option[String] = None,
                otherInfo: Option[String] = None,
                price: Option[String] = None,
                currency: Option[String] = None,
                creationTime: DateTime = DateTime.now(),
                creator: Option[UUID] = None,
                image: Option[ImageLinks] = None,
                status: WishStatus = WishStatus.Active,
                statusLastUpdate: Option[DateTime] = None,
                reserver: Option[UUID] = None,
                pastReservers: List[UUID] = Nil) {

  def withName(name: String): Wish = this.copy(name = Option(name))
  def withLink(link: String): Wish = this.copy(link = Option(link))
  def withStore(store: String): Wish = this.copy(store = Option(store))
  def withOtherInfo(info: String): Wish = this.copy(otherInfo = Option(info))
  def withPrice(price: String): Wish = this.copy(price = Option(price))
  def withCurrency(currency: String): Wish = this.copy(currency = Option(currency))
  def withoutImage: Wish = this.copy(image = None)
  def withCreationTime(time: DateTime): Wish = this.copy(creationTime = time)
  def withCreator(creator: UUID): Wish = this.copy(creator = Option(creator))
  def withImage(imageLinks: ImageLinks): Wish = this.copy(image = Option(imageLinks.copy(links = imageLinks.links.sortBy(_.width))))
  def withStatus(status: WishStatus, time: DateTime): Wish = this.copy(status = status, statusLastUpdate = Option(time))
  def withReserver(reserver: UUID): Wish = this.copy(reserver = Option(reserver), pastReservers = pastReservers :+ reserver)
  def withNoReserver: Wish = this.copy(reserver = None)
}

case class UserWishes(wishes: List[Wish])

case class ImageLink(url: String, width: Int, height: Int, contentType: String)

case class ImageLinks(links: List[ImageLink])

object WishStatus {
  sealed trait WishStatus
  case object Active extends WishStatus
  case class Granted (by: Option[UUID] = None) extends WishStatus
  case object Deleted extends WishStatus
  case class Reserved(by: UUID) extends WishStatus
}