package co.wishkeeper.server

import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

import co.wishkeeper.json._
import co.wishkeeper.server.Events.UserEvent
import co.wishkeeper.server.user.events.history.{HistoryEvent, HistoryEventInstance}
import com.datastax.driver.core._
import io.circe.generic.extras
import io.circe.generic.extras.auto._
import io.circe.parser.decode
import io.circe.syntax._
import org.joda.time.DateTime

import scala.collection.JavaConverters._


trait DataStore {
  def deleteWishHistoryEvent(userId: UUID, wishId: UUID): Boolean

  def truncateHistory(): Boolean

  def historyFor(userId: UUID): List[HistoryEventInstance]

  def saveUserHistoryEvent(userId: UUID, time: DateTime, event: HistoryEvent, wishId: UUID): Boolean

  def allUserEvents(eventTypes: Class[_ <: UserEvent]*): Iterator[UserEventInstance[_ <: UserEvent]]

  def userNames(): List[UserNameSearchRow]

  def saveUserByName(userByNameRow: UserNameSearchRow): Boolean

  def saveUserByName(rows: List[UserNameSearchRow]): Boolean

  def userByEmail(email: String): Option[UUID]

  def saveUserByEmail(email: String, userId: UUID): Boolean

  def userIdsByFacebookIds(facebookIds: List[String]): Map[String, UUID]

  def userIdByFacebookId(facebookId: String): Option[UUID]

  def saveUserIdByFacebookId(facebookId: String, userId: UUID): Boolean

  def userBySession(session: UUID): Option[UUID]

  def saveUserSession(userId: UUID, sessionId: UUID, created: DateTime): Boolean

  def saveUserEvents(userId: UUID, lastSeqNum: Option[Long], time: DateTime, events: Seq[UserEvent]): Boolean

  def lastSequenceNum(userId: UUID): Option[Long]

  def userEvents(userId: UUID): List[UserEventInstant[_ <: UserEvent]]

  def connect(): Unit

  def close(): Unit
}

case class DataStoreConfig(addresses: List[String])

class CassandraDataStore(dataStoreConfig: DataStoreConfig) extends DataStore {


  import CassandraDataStore._

  private implicit val circeConfig = extras.Configuration.default.withDefaults

  val cluster = Cluster.builder().addContactPoints(dataStoreConfig.addresses: _*).build()
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
  private lazy val insertUserByEmail = session.prepare(s"insert into $userByEmailTable (email, userId) values (:email, :userId) if not exists")
  private lazy val selectUserByEmail = session.prepare(s"select userId from $userByEmailTable where email = :email")
  private lazy val insertUserByName = session.prepare(
    s"""insert into $userByNameTable (userId, name, picture, first_name, last_name)
       |values (:userId, :name, :picture, :firstName, :lastName)""".stripMargin)
  private lazy val selectAllNamesToUserIds = session.prepare(s"select * from $userByNameTable")
  private lazy val selectAllUserEvents = session.prepare(s"select * from $userEventsTable")
  private lazy val saveUserHistoryEvent = session.prepare(
    s"insert into $historyTable (userId, wishId, time, event) values (:userId, :wishId, :time, :event)")
  private lazy val selectUserHistory = session.prepare(s"select * from $historyTable where userId = :userId")
  private lazy val truncateHistoryTable = session.prepare(s"truncate table $historyTable")
  private lazy val deleteUserHistoryByWishId = session.prepare(s"delete from $historyTable where userId = :userId and wishId = :wishId")


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

  override def userEvents(userId: UUID): List[UserEventInstant[_ <: UserEvent]] = {
    val resultSet: ResultSet = session.execute(selectUserEvents.bind().setUUID("userId", userId))
    resultSet.asScala.map(rowToEventInstant).toList
  }

  private val rowToEventInstant: Row => UserEventInstant[_ <: UserEvent] = row => {
    val json = new String(row.getBytes("event").array())
    val time = new DateTime(row.getTimestamp("time"))
    val eventOrError = decode[UserEvent](json)
    eventOrError match {
      case Right(event: UserEvent) ⇒ UserEventInstant(event, time)
      case Left(err: Throwable) ⇒ throw err
      case Left(err) ⇒ throw new RuntimeException(s"Error decoding json: $json [${err.toString}]")
    }
  }

  private val rowToEventInstance: Row => UserEventInstance[_ <: UserEvent] = row => {
    val instant = rowToEventInstant(row)
    UserEventInstance(row.getUUID("userId"), instant.event, instant.time)
  }

  override def userBySession(sessionId: UUID): Option[UUID] = {
    val result = session.execute(selectUserSession.bind().setUUID("sessionId", sessionId))
    if (result.getAvailableWithoutFetching > 0) {
      Option(result.one().getUUID("userId"))
    }
    else None
  }

  override def saveUserSession(userId: UUID, sessionId: UUID, created: DateTime = DateTime.now()): Boolean = {
    val resultSet = session.execute(insertUserSession.bind().
      setUUID("userId", userId).
      setUUID("sessionId", sessionId).
      setTimestamp("created", created.toDate))
    resultSet.wasApplied()
  }

  override def saveUserIdByFacebookId(facebookId: String, userId: UUID): Boolean = {
    session.execute(insertUserByFacebookId.bind().setString("facebookId", facebookId).setUUID("userId", userId)).wasApplied()
  }

  override def userIdByFacebookId(facebookId: String): Option[UUID] = {
    val resultSet = session.execute(selectUserByFacebookId.bind().setString("facebookId", facebookId))
    userIdFromSingleResult(resultSet)
  }

  override def userIdsByFacebookIds(facebookIds: List[String]): Map[String, UUID] = {
    val resultSet = session.execute(selectUsersByFacebookIds.bind().setList("idList", facebookIds.asJava))
    resultSet.asScala.map(row => row.getString("facebookId") -> row.getUUID("userId")).toMap
  }

  override def userByEmail(email: String): Option[UUID] = {
    val resultSet = session.execute(selectUserByEmail.bind().setString("email", email))
    userIdFromSingleResult(resultSet)
  }

  private def userIdFromSingleResult(resultSet: ResultSet) = {
    if (resultSet.getAvailableWithoutFetching > 0)
      Option(resultSet.one().getUUID("userId"))
    else
      None
  }

  override def saveUserByEmail(email: String, userId: UUID): Boolean = {
    session.execute(insertUserByEmail.bind().setString("email", email).setUUID("userId", userId)).wasApplied()
  }

  private def bindInsertUserByName(userByNameRow: UserNameSearchRow) = {
    insertUserByName.bind()
      .setUUID("userId", userByNameRow.userId)
      .setString("name", userByNameRow.name)
      .setString("picture", userByNameRow.picture.orNull)
      .setString("firstName", userByNameRow.firstName.orNull)
      .setString("lastName", userByNameRow.lastName.orNull)
  }

  override def saveUserByName(userByNameRow: UserNameSearchRow): Boolean = {
    val statement = bindInsertUserByName(userByNameRow)
    session.execute(statement).wasApplied()
  }

  override def saveUserByName(rows: List[UserNameSearchRow]): Boolean = {
    val batch = new BatchStatement()
    rows.foreach(row => batch.add(bindInsertUserByName(row)))
    session.execute(batch).wasApplied()
  }

  override def userNames(): List[UserNameSearchRow] = {
    session.execute(selectAllNamesToUserIds.bind()).asScala.map(row =>
      UserNameSearchRow(
        userId = row.getUUID("userId"),
        name = row.getString("name"),
        picture = Option(row.getString("picture")),
        firstName = Option(row.getString("first_name")),
        lastName = Option(row.getString("last_name"))
      )).toList
  }

  override def allUserEvents(eventTypes: Class[_ <: UserEvent]*): Iterator[UserEventInstance[_ <: UserEvent]] = {
    session.execute(selectAllUserEvents.bind()).iterator().asScala.map(rowToEventInstance).
      filter(instant => eventTypes.contains(instant.event.getClass))
  }

  override def saveUserHistoryEvent(userId: UUID, time: DateTime, event: HistoryEvent, wishId: UUID): Boolean = {
    val eventJson = event.asJson.noSpaces
    session.execute(saveUserHistoryEvent.bind()
      .setUUID("userId", userId)
      .setUUID("wishId", wishId)
      .setTimestamp("time", time.toDate)
      .setBytes("event", ByteBuffer.wrap(eventJson.getBytes))).wasApplied()
  }

  override def historyFor(userId: UUID): List[HistoryEventInstance] =
    session.execute(selectUserHistory.bind().setUUID("userId", userId)).asScala.map(row => {
      val json = new String(row.getBytes("event").array())
      val time = new DateTime(row.getTimestamp("time"))
      val wishId = row.getUUID("wishId")
      val eventOrError = decode[HistoryEvent](json)
      eventOrError match {
        case Right(event: HistoryEvent) => HistoryEventInstance(userId, wishId, time, event)
        case Left(err: Throwable) ⇒ throw err
        case Left(err) ⇒ throw new RuntimeException(s"Error decoding json: $json [${err.toString}]")
      }
    }).toList

  override def truncateHistory(): Boolean = session.execute(truncateHistoryTable.bind()).wasApplied()

  override def deleteWishHistoryEvent(userId: UUID, wishId: UUID): Boolean =
    session.execute(deleteUserHistoryByWishId.bind().setUUID("userId", userId).setUUID("wishId", wishId)).wasApplied()

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
  val userEventsTable: String = keyspace + ".user_events"
  val userByFacebookId: String = keyspace + ".user_by_facebook_id"
  val userSession: String = keyspace + ".user_session"
  val userByEmailTable: String = keyspace + ".user_by_email"
  val userByNameTable: String = keyspace + ".user_by_name"
  val historyTable: String = keyspace + ".history"
}

case class UserNameSearchRow(userId: UUID, name: String, picture: Option[String] = None, firstName: Option[String] = None,
                             lastName: Option[String] = None)