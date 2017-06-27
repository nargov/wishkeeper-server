package co.wishkeeper.server

import java.util.UUID

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
                image: Option[ImageLink] = None) {


  def withName(name: String): Wish = this.copy(name = Option(name))
  def withLink(link: String): Wish = this.copy(link = Option(link))
  def withStore(store: String): Wish = this.copy(store = Option(store))
  def withOtherInfo(info: String): Wish = this.copy(otherInfo = Option(info))
  def withPrice(price: String): Wish = this.copy(price = Option(price))
  def withCurrency(currency: String): Wish = this.copy(currency = Option(currency))
  def withoutImage: Wish = this.copy(image = None)
  def withCreationTime(time: DateTime): Wish = this.copy(creationTime = time)
  def withCreator(creator: UUID): Wish = this.copy(creator = Option(creator))
  def withImage(imageLink: ImageLink): Wish = this.copy(image = Option(imageLink))
}

case class UserWishes(wishes: List[Wish])

case class ImageLink(url: String, width: Int, height: Int, contentType: String)