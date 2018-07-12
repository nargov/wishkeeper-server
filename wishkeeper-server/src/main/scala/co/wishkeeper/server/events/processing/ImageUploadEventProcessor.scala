package co.wishkeeper.server.events.processing

import java.util.UUID

import co.wishkeeper.server.Events.UserPictureSet
import co.wishkeeper.server.image.{ImageData, ImageStore}
import co.wishkeeper.server.{EventProcessor, Events, FileAdapter}

class ImageUploadEventProcessor(imageStore: ImageStore, fileAdapter: FileAdapter) extends EventProcessor {
  override def process(event: Events.Event, userId: UUID): List[(UUID, Events.Event)] = {
    event match {
      case UserPictureSet(_, url) if !url.startsWith(imageStore.imageLinkBase) =>
        val newImageId = UUID.randomUUID().toString
        fileAdapter.inputStreamFor(url)
          .map(in => imageStore.save(ImageData(in, "image/png"), newImageId))
          .map(_ => (userId, UserPictureSet(userId, imageStore.imageLinkBase + newImageId)) :: Nil).getOrElse(Nil)
      case _ => Nil
    }
  }
}
