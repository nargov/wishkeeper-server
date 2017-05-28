package co.wishkeeper.server

import org.specs2.mutable.Specification
import org.specs2.specification.{AfterAll, Scope}

class GoogleCloudStorageImageStoreIT extends Specification with AfterAll {
  sequential

  val imageStore: ImageStore = new GoogleCloudStorageImageStore
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