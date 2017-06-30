package co.wishkeeper.server

import java.nio.file.{Files, Path, Paths}

import com.sksamuel.scrimage.Image
import com.sksamuel.scrimage.nio.JpegWriter

trait ImageProcessor {
  def compress(src: Path, extension: String): Path

  def resizeToWidth(src: Path, extension: String, width: Int): Path

  def dimensions(src: Path): (Int, Int)
}

object ImageProcessor {
  val tempDir: Path = Files.createDirectories(Paths.get("/tmp/wishkeeper-resize"))
}

class ScrimageImageProcessor extends ImageProcessor {
  
  override def dimensions(src: Path): (Int, Int) = {
    val image = Image.fromPath(src)
    (image.width, image.height)
  }

  private val jpegWriter = JpegWriter().withCompression(70)

  private def addExtension(origFile: Path, extension: String) = {
    origFile.resolveSibling(origFile.getName(origFile.getNameCount - 1).toString + extension)
  }

  override def compress(src: Path, extension: String): Path = {
    val compressed = addExtension(src, extension)
    Image.fromPath(src).output(compressed)(jpegWriter)
  }

  override def resizeToWidth(src: Path, extension: String, width: Int): Path = {
    val resized: Path = addExtension(src, extension)
    val currentWidth = Image.fromPath(src).width
    Image.fromPath(src).scale(width.toDouble / currentWidth.toDouble).output(resized)(jpegWriter)
  }
}