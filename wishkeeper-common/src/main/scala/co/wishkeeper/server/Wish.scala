package co.wishkeeper.server

import java.util.UUID

case class Wish(id: UUID,
                name: Option[String] = None,
                link: Option[String] = None,
                imageLink: Option[String] = None,
                store: Option[String] = None,
                otherInfo: Option[String] = None,
                price: Option[String] = None,
                currency: Option[String] = None
               ) {


  def withName(name: String): Wish = this.copy(name = Option(name))
  def withLink(link: String): Wish = this.copy(link = Option(link))
  def withImageLink(link: String): Wish = this.copy(imageLink = Option(link))
  def withStore(store: String): Wish = this.copy(store = Option(store))
  def withOtherInfo(info: String): Wish = this.copy(otherInfo = Option(info))
  def withPrice(price: String): Wish = this.copy(price = Option(price))
  def withCurrency(currency: String): Wish = this.copy(currency = Option(currency))
}