package co.wishkeeper.server.events.processing

import java.util.UUID

import co.wishkeeper.server.Events.{UserEvent, UserPictureSet}
import co.wishkeeper.server.image.{ImageData, ImageStore}
import co.wishkeeper.server._
import org.joda.time.DateTime

class ImageUploadEventProcessor(imageStore: ImageStore, fileAdapter: FileAdapter, dataStore: DataStore) extends EventProcessor {
  override def process[E <: UserEvent](instance: UserEventInstance[E]): List[UserEventInstance[_ <: UserEvent]] = {
    instance.event match {
      case UserPictureSet(_, url) if !url.startsWith(imageStore.imageLinkBase) =>
        val newImageId = UUID.randomUUID().toString
        val userId = instance.userId
        val pictureSetEvent = UserPictureSet(userId, imageStore.imageLinkBase + "/" + newImageId)
        val now = DateTime.now()
        fileAdapter.inputStreamFor(url)
          .map(in => {
            imageStore.save(ImageData(in, "image/png"), newImageId)
            dataStore.saveUserEvents(userId, dataStore.lastSequenceNum(userId), now, pictureSetEvent :: Nil)
          })
          .map(_ => UserEventInstance(userId, pictureSetEvent, now) :: Nil).getOrElse(Nil)
      case _ => Nil
    }
  }
}
