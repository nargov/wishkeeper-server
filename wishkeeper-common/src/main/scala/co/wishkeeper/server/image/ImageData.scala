package co.wishkeeper.server.image

import java.io.InputStream

case class ImageData(content: InputStream, contentType: String) {
  def readFully(): Array[Byte] = Iterator.continually(content.read()).takeWhile(_ != -1).map(_.toByte).toArray
}

object ImageData {
  val defaultContentType = "application/octet-stream"
}