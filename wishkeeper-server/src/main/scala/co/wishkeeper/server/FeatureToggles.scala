package co.wishkeeper.server

import java.util.UUID

import org.slf4j.{Logger, LoggerFactory}

import scala.io.Source
import scala.util.Try

trait FeatureToggles {
  def isTestUser(userId: UUID): Boolean

}

class StaticFileFeatureToggles extends FeatureToggles {
  private val logger: Logger = LoggerFactory.getLogger(getClass.getName)

  val testUsers: Set[UUID] = Try {
    Source.fromResource("test-users").getLines().toSet.map(UUID.fromString)
  }.recover {
    case t: Throwable =>
      logger.info("No test users file found")
      Set.empty[UUID]
  }.get

  override def isTestUser(userId: UUID): Boolean = testUsers.contains(userId)
}