package co.wishkeeper

import java.io.{ByteArrayInputStream, InputStream}

import com.google.cloud.storage.Bucket.BlobWriteOption
import com.google.cloud.storage.{Blob, Storage, StorageOptions}
import org.specs2.mutable.Specification

class GoogleCloudLearningTest extends Specification {

  "GCP" should {
    "save file with metadata" in {
      val storage = StorageOptions.getDefaultInstance.getService
      val bucketName = "wish.media.wishkeeper.co"
      val bucket = storage.get(bucketName)
      bucket aka "bucket" must not beNull
      val blobName = "test2"
      val content = """{"message": "this is a test"}"""
      val stream: InputStream = new ByteArrayInputStream(content.getBytes)
      bucket.create(blobName, stream, "application/json", BlobWriteOption.predefinedAcl(Storage.PredefinedAcl.PUBLIC_READ)) must beAnInstanceOf[Blob]
    }
  }
}