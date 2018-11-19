package co.wishkeeper.server

import java.io.InputStream
import java.net.{URL, URLConnection}

import scala.util.Try

trait FileAdapter {
  def emailTemplate(str: String): Try[String]

  def inputStreamFor(str: String): Try[InputStream]
}

class JavaFileAdapter extends FileAdapter {
  override def inputStreamFor(url: String): Try[InputStream] = {
    Try {
      val connection: URLConnection = new URL(url).openConnection()
      connection.connect()
      connection.getInputStream
    }
  }

  override def emailTemplate(str: String): Try[String] = ???
}
