package co.wishkeeper.server.image

import cats.data.EitherT
import co.wishkeeper.server.{Error, GeneralError, ImageLink, ImageLinks}
import com.cloudinary.Cloudinary

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.existentials
import scala.util.Try

trait BetterImageStore {
  def save(url: String, id: String): EitherT[Future, Error, ImageLinks]
}

class CloudinaryImageStore(config: CloudinaryConfig)(implicit ec: ExecutionContext) extends BetterImageStore {
  val cloudinary = new Cloudinary(Map("cloud_name" -> config.cloudName, "api_key" -> config.apiKey, "api_secret" -> config.apiSecret).asJava)

  def save(url: String, id: String): EitherT[Future, Error, ImageLinks] = {
    EitherT(Future {
      Try {
        cloudinary.uploader().upload(url, Map[String, Any]("public_id" -> id).asJava)
      }.toEither.left.map(t => GeneralError(t.getMessage))
        .map(response => {
          val secureUrl = response.get("secure_url").asInstanceOf[String]
          val upload = "upload/"
          val (urlBase, urlSuffix) = secureUrl.splitAt(secureUrl.indexOf(upload) + upload.length)
          val origWidth = response.get("width").asInstanceOf[Int]
          val origHeight = response.get("height").asInstanceOf[Int]
          val format = response.get("format").asInstanceOf[String]
          val links = ImageLink(
            secureUrl,
            origWidth,
            origHeight,
            format
          ) +: ImageUploader.sizeExtensions.collect { case (_, width) if width < origWidth =>
            ImageLink(
              urlBase + "c_scale,w_" + width + "/" + urlSuffix,
              width,
              origHeight * width / origWidth,
              format
            )
          }
          ImageLinks(links)
        })
    })
  }
}

case class CloudinaryConfig(cloudName: String, apiKey: String, apiSecret: String)

case class CloudinarySaveResponse()
