package co.wishkeeper.server

import java.nio.file.{Files, Paths}

class TestImage {
  val imageFile = "/smiley.jpg"
  val contentType = "image/jpeg"
  val width = 128
  val height = 128
  val resource = getClass.getResource(imageFile)
  lazy val imageData = ImageData(resource.openStream(), contentType)
  val path = Paths.get(resource.toURI)

  def fileBytes: Array[Byte] = {
    Files.readAllBytes(path)
  }
}
