package co.wishkeeper.server.events.processing

import java.util.UUID

import co.wishkeeper.server.Events.UserPictureSet
import co.wishkeeper.server.image.{ImageData, ImageStore}
import co.wishkeeper.server.{DataStore, EventProcessor, Events, FileAdapter}
import org.joda.time.DateTime

class ImageUploadEventProcessor(imageStore: ImageStore, fileAdapter: FileAdapter, dataStore: DataStore) extends EventProcessor {
  override def process(event: Events.Event, userId: UUID): List[(UUID, Events.Event)] = {
    event match {
      case UserPictureSet(_, url) if !url.startsWith(imageStore.imageLinkBase) =>
        val newImageId = UUID.randomUUID().toString
        val pictureSetEvent = UserPictureSet(userId, imageStore.imageLinkBase + newImageId)
        fileAdapter.inputStreamFor(url)
          .map(in => {
            imageStore.save(ImageData(in, "image/png"), newImageId)
            dataStore.saveUserEvents(userId, dataStore.lastSequenceNum(userId), DateTime.now(), pictureSetEvent :: Nil)
          })
          .map(_ => (userId, pictureSetEvent) :: Nil).getOrElse(Nil)
      case _ => Nil
    }
  }
}
