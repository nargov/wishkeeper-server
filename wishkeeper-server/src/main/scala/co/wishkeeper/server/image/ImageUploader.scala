package co.wishkeeper.server.image

import java.io.InputStream
import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{Executors, ThreadFactory}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import co.wishkeeper.server.{ImageLink, ImageLinks, ImageProcessor}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class ImageUploader(imageStore: ImageStore, userImageStore: ImageStore, imageProcessor: ImageProcessor, maxUploadThreads: Int = 20)
                   (implicit actorSystem: ActorSystem, am: ActorMaterializer) {

  private implicit val ec = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(maxUploadThreads, new ThreadFactory {
    private val threadIdCounter = new AtomicInteger(0)

    override def newThread(r: Runnable): Thread = new Thread(r, s"image-uploader-${threadIdCounter.getAndIncrement()}")
  }))

  def uploadImageAndResizedCopies(imageMetadata: ImageMetadata, origFile: Path, timeout: Duration = 2.minutes, toImageStore: ImageStore = imageStore,
                                  extensions: List[(String, Int)] = ImageUploader.sizeExtensions): ImageLinks = {

    val sizesAndExtensions = (".full", imageMetadata.width) :: extensions
    val (origImageWidth, _) = imageProcessor.dimensions(origFile)

    val eventualLinks: List[Future[ImageLink]] = sizesAndExtensions.filter {
      case (_, width) => width <= origImageWidth
    }.map { case (ext, width) =>
      Future {
        val file = if (width == origImageWidth)
          imageProcessor.compress(origFile, ext)
        else {
          imageProcessor.resizeToWidth(origFile, ext, width)
        }
        val fileName = file.getFileName.toString
        val (processedWidth, processedHeight) = imageProcessor.dimensions(file)
        toImageStore.save(ImageData(Files.newInputStream(file), ContentTypes.jpeg), fileName)
        ImageLink(s"${toImageStore.imageLinkBase}/$fileName", processedWidth, processedHeight, ContentTypes.jpeg)
      }
    }

    //TODO return a future from this function instead of blocking
    val imageLinks: List[ImageLink] = Await.result(Future.sequence(eventualLinks), timeout)

    Files.deleteIfExists(origFile)

    ImageLinks(imageLinks)
  }

  def uploadProfileImage(inputStream: InputStream, imageMetadata: ImageMetadata): ImageLinks = {
    val tempFile = Paths.get(ImageProcessor.tempDir.toString, imageMetadata.fileName)
    Files.copy(inputStream, tempFile)
    val meta = if (imageMetadata.width > 0 && imageMetadata.height > 0) imageMetadata
    else {
      val (width, height) = imageProcessor.dimensions(tempFile)
      imageMetadata.copy(width = width, height = height)
    }
    uploadImageAndResizedCopies(meta, tempFile, toImageStore = userImageStore, extensions = ImageUploader.profilePicSizeExtensions)
  }
}

object ImageUploader {
  val sizeExtensions = List(
    (".fhd", 1080),
    (".hfhd", 540),
    (".qfhd", 270)
  )

  val profilePicSizeExtensions = List(".small" -> 200)
}

object ContentTypes {
  val jpeg = "image/jpeg"
}
