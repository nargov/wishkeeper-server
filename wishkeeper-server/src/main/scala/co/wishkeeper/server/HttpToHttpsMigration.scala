package co.wishkeeper.server

import co.wishkeeper.server.Events.{UserEvent, UserPictureSet, WishImageSet}
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

import scala.util.Try

object HttpToHttpsMigration {

  val oldUserPrefix = "http://user.media.wishkeeper.co/"
  val newUserPrefix = "https://user-media.wishkeeper.co/"
  val oldWishPrefix = "http://wish.media.wishkeeper.co/"
  val newWishPrefix = "https://wish-media.wishkeeper.co/"

  val migratedUserImage: User => Option[UserPictureSet] = user => user.userProfile.picture.flatMap(currentPic => {
    if (currentPic.startsWith(oldUserPrefix)) {
      Option(UserPictureSet(user.id, newUserPrefix + currentPic.substring(oldUserPrefix.length)))
    }
    else None
  })


  def migratedWishImages(user: User): List[WishImageSet] = user.wishes.values.flatMap { wish =>
    wish.image.map(links => {
      if (links.links.head.url.startsWith(oldWishPrefix))
        Option(WishImageSet(wish.id, links.copy(links = links.links.map(link => {
          link.copy(url = newWishPrefix + link.url.substring(oldWishPrefix.length))
        }))))
      else
        None
    })
  }.flatten.toList

  def migrate(dataStore: DataStore): Unit = {
    dataStore.userEmails.foreach { case (_, userId) =>
      Try {
        val user = User.replay(dataStore.userEvents(userId))
        val userPicEvents: List[UserEvent] = migratedUserImage(user).toList
        val wishImageEvents: List[UserEvent] = migratedWishImages(user)
        val events = userPicEvents ++ wishImageEvents
        if (events.nonEmpty)
          dataStore.saveUserEvents(user.id, dataStore.lastSequenceNum(user.id), DateTime.now(), events)
      }.recover {
        case e: Exception => LoggerFactory.getLogger(getClass).error(s"Error when processing user [$userId]", e)
      }
    }
  }
}
