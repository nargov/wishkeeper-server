package co.wishkeeper.server

import java.nio.file.{Files, Paths}

import org.specs2.mutable.Specification
import org.specs2.specification.{AfterAll, Scope}

class GoogleCloudStorageImageStoreIT extends Specification with AfterAll {
  sequential

  val imageStore: ImageStore = new GoogleCloudStorageImageStore
  val id = "test-image-file"

  "GoogleCloudStorageImageStore" should {
    "Save a file" in new Context {
      saveImage()

      readImage().map(_.readFully()) must beSome(fileBytes)
    }

    "Save file content-type" in new Context {
      saveImage()

      readImage().map(_.contentType) must beSome(contentType)
    }
  }


  override def afterAll(): Unit = imageStore.delete(id)

  trait Context extends Scope {
    val imageFile = "/smiley.jpg"
    val contentType = "image/jpeg"
    val imageData = ImageData(getClass.getResourceAsStream(imageFile), contentType)

    def readImage(): Option[ImageData] = {
      imageStore.read(id)
    }

    def saveImage(): Unit = {
      imageStore.save(imageData, id)
    }

    def fileBytes: Array[Byte] = Files.readAllBytes(Paths.get(getClass.getResource(imageFile).toURI))
  }

}
