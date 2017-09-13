package co.wishkeeper.server.image

import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{Executors, ThreadFactory}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import co.wishkeeper.server.{ImageLink, ImageLinks, ImageProcessor}
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class WishImages(imageStore: ImageStore, imageProcessor: ImageProcessor, maxUploadThreads: Int = 20)
                (implicit actorSystem: ActorSystem, am: ActorMaterializer) {

  private implicit val ec = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(maxUploadThreads, new ThreadFactory {
    private val threadIdCounter = new AtomicInteger(0)
    override def newThread(r: Runnable): Thread = new Thread(r, s"wish-images-${threadIdCounter.getAndIncrement()}")
  }))

  private val log = LoggerFactory.getLogger(getClass)

  def uploadImageAndResizedCopies(imageMetadata: ImageMetadata, origFile: Path, timeout: Duration = 2.minutes): ImageLinks = {
    val sizesAndExtensions = (".full", imageMetadata.width) :: WishImages.sizeExtensions

    val eventualLinks: List[Future[ImageLink]] = sizesAndExtensions.filter {
      case (_, width) => width <= imageMetadata.width
    }.map { case (ext, width) =>
      Future {
        val file = if (width == imageMetadata.width)
          imageProcessor.compress(origFile, ext)
        else
          imageProcessor.resizeToWidth(origFile, ext, width)
        val fileName = file.getFileName.toString
        val (_, height) = imageProcessor.dimensions(file)
        log.debug(s"Uploading $fileName to cloud Storage")
        imageStore.save(ImageData(Files.newInputStream(file), ContentTypes.jpeg), fileName)
        log.debug(s"Done uploading $fileName to cloud Storage")
        Files.deleteIfExists(file)
        ImageLink(s"${imageStore.imageLinkBase}/$fileName", width, height, ContentTypes.jpeg)
      }
    }

    val imageLinks: List[ImageLink] = Await.result(Future.sequence(eventualLinks), timeout)

    Files.deleteIfExists(origFile)

    ImageLinks(imageLinks)
  }
}

object WishImages {
  val sizeExtensions = List(
    (".fhd", 1080),
    (".hfhd", 540),
    (".qfhd", 270)
  )
}

object ContentTypes {
  val jpeg = "image/jpeg"
}
