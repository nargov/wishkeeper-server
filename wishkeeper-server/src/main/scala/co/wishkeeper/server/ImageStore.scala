package co.wishkeeper.server

import java.io.InputStream
import java.nio.channels.Channels

import co.wishkeeper.server.GoogleCloudStorageImageStore.bucketName
import com.google.cloud.storage.Bucket.BlobWriteOption
import com.google.cloud.storage.Storage.{BlobField, BlobGetOption}
import com.google.cloud.storage.{BlobId, Storage, StorageOptions}

trait ImageStore {
  def delete(id: String): Unit

  def read(id: String): Option[ImageData]

  def save(image: ImageData, id: String): Unit
}

class GoogleCloudStorageImageStore extends ImageStore {
  private val storage = StorageOptions.getDefaultInstance.getService

  override def save(image: ImageData, id: String): Unit = {
    val maybeBucket = Option(storage.get(bucketName))
    maybeBucket.map { bucket =>
      bucket.create(id, image.content, image.contentType, BlobWriteOption.predefinedAcl(Storage.PredefinedAcl.PUBLIC_READ))
    }
  }

  override def read(id: String): Option[ImageData] = {
    val maybeBlob = Option(storage.get(BlobId.of(bucketName, id), BlobGetOption.fields(BlobField.CONTENT_TYPE)))
    maybeBlob.map { blob =>
      ImageData(Channels.newInputStream(blob.reader()), blob.getContentType)
    }
  }

  override def delete(id: String): Unit = storage.delete(BlobId.of(bucketName, id))
}

object GoogleCloudStorageImageStore {
  private val bucketName = "wish.media.wishkeeper.co"
}

case class ImageData(content: InputStream, contentType: String) {
  def readFully(): Array[Byte] = Iterator.continually(content.read()).takeWhile(_ != -1).map(_.toByte).toArray
}
object ImageData {
  val defaultContentType = "application/octet-stream"
}
