package co.wishkeeper.server

import java.nio.file.{Files, Paths}

import co.wishkeeper.server.image.ImageData

class TestImage {
  val imageFile = "/smiley.jpg"
  val contentType = "image/jpeg"
  val width = 128
  val height = 128
  lazy val resource = getClass.getResource(imageFile)
  lazy val imageData = ImageData(resource.openStream(), contentType)
  lazy val path = Paths.get(resource.toURI)

  def fileBytes: Array[Byte] = {
    Files.readAllBytes(path)
  }
}
class LargeTestImage extends TestImage {
  override val imageFile = "/bat.jpg"
  override val width = 1280
  override val height = 720
}

object TestImage {
  def apply() = new TestImage
  def large() = new LargeTestImage
}