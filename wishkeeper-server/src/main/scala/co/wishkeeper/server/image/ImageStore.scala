package co.wishkeeper.server.image

import java.nio.channels.Channels

import com.google.cloud.storage.Bucket.BlobWriteOption
import com.google.cloud.storage.Storage.{BlobField, BlobGetOption}
import com.google.cloud.storage.{BlobId, Storage, StorageOptions}

trait ImageStore {
  def delete(id: String): Boolean

  def read(id: String): Option[ImageData]

  def save(image: ImageData, id: String): Unit

  def imageLinkBase: String
}

class GoogleCloudStorageImageStore(bucket: String) extends ImageStore {
  private val storage = StorageOptions.getDefaultInstance.getService

  override def save(image: ImageData, id: String): Unit = {
    val maybeBucket = Option(storage.get(bucket))
    maybeBucket.map { bucket =>
      bucket.create(id, image.content, image.contentType, BlobWriteOption.predefinedAcl(Storage.PredefinedAcl.PUBLIC_READ))
    }
  }

  override def read(id: String): Option[ImageData] = {
    val maybeBlob = Option(storage.get(BlobId.of(bucket, id), BlobGetOption.fields(BlobField.CONTENT_TYPE)))
    maybeBlob.map { blob =>
      ImageData(Channels.newInputStream(blob.reader()), blob.getContentType)
    }
  }

  override def delete(id: String): Boolean = storage.delete(BlobId.of(bucket, id))

  override def imageLinkBase = s"https://$bucket"
}




case class ImageMetadata(contentType: String, fileName: String, width: Int = 0, height: Int = 0)