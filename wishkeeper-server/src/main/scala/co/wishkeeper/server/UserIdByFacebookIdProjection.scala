package co.wishkeeper.server
import java.util.UUID

trait UserIdByFacebookIdProjection {

  def get(facebookId: String): Option[UUID]

  def get(facebookIds: List[String]): Map[String, UUID]

  def process(event: Events.Event): Unit
}
