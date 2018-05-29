package co.wishkeeper.server

import co.wishkeeper.server.image.{GoogleCloudStorageImageStore, ImageData, ImageStore}
import org.specs2.mutable.Specification
import org.specs2.specification.{AfterAll, Scope}
import GoogleCloudStorageImageStoreIT.devBucket

class GoogleCloudStorageImageStoreIT extends Specification with AfterAll {
  sequential

  val imageStore: ImageStore = new GoogleCloudStorageImageStore(devBucket)
  val id = "test-image-file"

  "GoogleCloudStorageImageStore" should {
    "Save a file" in new Context {
      saveImage()

      readImage().map(_.readFully()) must beSome(testImage.fileBytes)
    }

    "Save file content-type" in new Context {
      saveImage()

      readImage().map(_.contentType) must beSome(testImage.contentType)
    }
  }

  override def afterAll(): Unit = imageStore.delete(id)

  trait Context extends Scope {
    val testImage = new TestImage

    def readImage(): Option[ImageData] = {
      imageStore.read(id)
    }

    def saveImage(): Unit = {
      imageStore.save(testImage.imageData, id)
    }
  }
}
object GoogleCloudStorageImageStoreIT {
  val devBucket = "wish.media.dev.wishkeeper.co"
}