package co.wishkeeper.server

import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

import co.wishkeeper.json._
import co.wishkeeper.server.Events.UserEvent
import com.datastax.driver.core._
import io.circe.generic.extras
import io.circe.generic.extras.auto._
import io.circe.parser.decode
import io.circe.syntax._
import org.joda.time.DateTime

import scala.collection.JavaConverters._


trait DataStore {

  def userIdsByFacebookIds(facebookIds: List[String]): Map[String, UUID]

  def userIdByFacebookId(facebookId: String): Option[UUID]

  def saveUserIdByFacebookId(facebookId: String, userId: UUID): Boolean

  def userBySession(session: UUID): Option[UUID]

  def saveUserSession(userId: UUID, sessionId: UUID, created: DateTime)

  def saveUserEvents(userId: UUID, lastSeqNum: Option[Long], time: DateTime, events: Seq[UserEvent]): Boolean

  def lastSequenceNum(userId: UUID): Option[Long]

  def userEventsFor(userId: UUID): List[UserEvent]

  def connect(): Unit

  def close(): Unit
}

class CassandraDataStore extends DataStore {

  import CassandraDataStore._

  private implicit val circeConfig = extras.Configuration.default.withDefaults

  val cluster = Cluster.builder().addContactPoint("localhost").build()
  private val clusterSession: AtomicReference[Session] = new AtomicReference(null)
  private def session = clusterSession.get()

  private lazy val selectMaxSeq = session.prepare(s"select seqMax from $userEventsTable where userId = :userId")
  private lazy val insertMaxSeq = session.prepare(s"insert into $userEventsTable (userId, seqMax) values (:userId, :newMax) if not exists")
  private lazy val updateMaxSeq = session.prepare(s"update $userEventsTable set seqMax = :newMax where userId = :userId if seqMax = :oldMax")
  private lazy val insertEvent = session.prepare(s"insert into $userEventsTable (userId, seq, time, event) values (:userId, :seq, :time, :event)")
  private lazy val selectUserEvents = session.prepare(s"select userId, seq, time, event from $userEventsTable where userId = :userId")
  private lazy val insertUserSession = session.prepare(s"insert into $userSession (sessionId, userId, created) values (:sessionId, :userId, :created)")
  private lazy val selectUserSession = session.prepare(s"select * from $userSession where sessionId = :sessionId")
  private lazy val insertUserByFacebookId = session.prepare(s"insert into $userByFacebookId (facebookId, userId) values (:facebookId, :userId)")
  private lazy val selectUserByFacebookId = session.prepare(s"select userId from $userByFacebookId where facebookId = :facebookId")
  private lazy val selectUsersByFacebookIds = session.prepare(s"select facebookId, userId from $userByFacebookId where facebookId in :idList")


  override def saveUserEvents(userId: UUID, lastSeqNum: Option[Long], time: DateTime, events: Seq[UserEvent]): Boolean = {
    val batch = new BatchStatement()

    val newMax = lastSeqNum.map(_ + events.size).getOrElse(events.size.toLong)
    val seqMaxQuery = lastSeqNum match {
      case Some(oldMax) ⇒ updateMaxSeq.bind().setLong("oldMax", oldMax).setLong("newMax", newMax)
      case None ⇒ insertMaxSeq.bind().setLong("newMax", newMax)
    }

    batch.add(seqMaxQuery.setUUID("userId", userId))

    val offset = lastSeqNum.getOrElse(0L) + 1

    events.zipWithIndex.foreach { case (event, i) ⇒
      val eventJson = event.asJson.noSpaces
      batch.add(insertEvent.bind().
        setUUID("userId", userId).
        setLong("seq", offset + i).
        setTimestamp("time", time.toDate).
        setBytes("event", ByteBuffer.wrap(eventJson.getBytes)))
    }
    session.execute(batch).wasApplied()
  }

  override def lastSequenceNum(userId: UUID): Option[Long] = {
    val resultSet = session.execute(selectMaxSeq.bind().setUUID("userId", userId))
    if (resultSet.getAvailableWithoutFetching > 0)
      Option(resultSet.one().getLong(0))
    else
      None
  }

  override def userEventsFor(userId: UUID): List[UserEvent] = {
    val resultSet: ResultSet = session.execute(selectUserEvents.bind().setUUID("userId", userId))
    resultSet.asScala.map(rowToUserEvent).toList
  }

  private val rowToUserEvent: Row ⇒ UserEvent = row ⇒ {
    val json = new String(row.getBytes("event").array())
    val eventOrError = decode[UserEvent](json)
    eventOrError match {
      case Right(event: UserEvent) ⇒ event
      case Left(err: Throwable) ⇒ throw err
      case Left(err) ⇒ throw new RuntimeException(s"Error decoding json: $json [${err.toString}]")
    }
  }

  override def userBySession(sessionId: UUID): Option[UUID] = {
    val result = session.execute(selectUserSession.bind().setUUID("sessionId", sessionId))
    if (result.getAvailableWithoutFetching > 0) {
      Option(result.one().getUUID("userId"))
    }
    else None
  }

  override def saveUserSession(userId: UUID, sessionId: UUID, created: DateTime = DateTime.now()): Unit = {
    session.execute(insertUserSession.bind().setUUID("userId", userId).setUUID("sessionId", sessionId).setTimestamp("created", created.toDate))
  }

  override def saveUserIdByFacebookId(facebookId: String, userId: UUID): Boolean = {
    session.execute(insertUserByFacebookId.bind().setString("facebookId", facebookId).setUUID("userId", userId)).wasApplied()
  }

  override def userIdByFacebookId(facebookId: String): Option[UUID] = {
    val resultSet = session.execute(selectUserByFacebookId.bind().setString("facebookId", facebookId))
    if (resultSet.getAvailableWithoutFetching > 0)
      Option(resultSet.one().getUUID("userId"))
    else
      None
  }

  override def userIdsByFacebookIds(facebookIds: List[String]): Map[String, UUID] = {
    val resultSet = session.execute(selectUsersByFacebookIds.bind().setList("idList", facebookIds.asJava))
    resultSet.asScala.map(row => row.getString("facebookId") -> row.getUUID("userId")).toMap
  }

  override def close(): Unit = {
    session.close()
    cluster.close()
  }

  override def connect(): Unit = {
    clusterSession.compareAndSet(null, cluster.connect())
  }
}

object CassandraDataStore {
  val keyspace = "wishkeeper"
  val userEventsTable = keyspace + ".user_events"
  val userByFacebookId = keyspace + ".user_by_facebook_id"
  val userSession = keyspace + ".user_session"
}