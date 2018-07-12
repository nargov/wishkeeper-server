package co.wishkeeper.server

import java.io.InputStream
import java.net.{URL, URLConnection}

import scala.util.Try

trait FileAdapter {
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
}
