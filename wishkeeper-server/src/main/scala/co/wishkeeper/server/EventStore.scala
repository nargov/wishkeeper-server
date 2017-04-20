package co.wishkeeper.server

import java.nio.ByteBuffer
import java.util.UUID

import co.wishkeeper.json._
import co.wishkeeper.server.Events.UserEvent
import com.datastax.driver.core.{BatchStatement, Cluster, ResultSet, Row}
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import org.joda.time.DateTime

import scala.collection.JavaConverters._


trait EventStore {
  def persistUserEvent(userId: UUID, lastSeqNum: Option[Long], time: DateTime, event: UserEvent): Boolean

  def persistUserEvents(userId: UUID, lastSeqNum: Option[Long], time: DateTime, events: Seq[UserEvent]): Unit

  def lastSequenceNum(userId: UUID): Option[Long]

  def userEventsFor(userId: UUID): List[UserEvent]

  def userInfoByFacebookId(facebookId: String): Option[UserInfoInstance]

  def updateFacebookIdToUserInfo(facebookId: String, lastSeq: Option[Long], userInfo: UserInfo): Boolean
}

class CassandraEventStore extends EventStore {
  val cluster = Cluster.builder().addContactPoint("localhost").build()
  val session = cluster.connect()
  private val keyspace = "wishkeeper"
  private val userEventsTable = keyspace + ".user_events"
  private val userInfoByFacebookIdTable = keyspace + ".user_info_by_facebook_id"
  private lazy val selectMaxSeq = session.prepare(s"select seqMax from $userEventsTable where userId = :userId")
  private lazy val insertMaxSeq = session.prepare(s"insert into $userEventsTable (userId, seqMax) values (:userId, :newMax) if not exists")
  private lazy val updateMaxSeq = session.prepare(s"update $userEventsTable set seqMax = :newMax where userId = :userId if seqMax = :oldMax")
  private lazy val insertEvent = session.prepare(s"insert into $userEventsTable (userId, seq, time, event) values (:userId, :seq, :time, :event)")
  private lazy val selectUserEvents = session.prepare(s"select userId, seq, time, event from $userEventsTable where userId = :userId")
  private lazy val selectUserInfoByFacebookId = session.prepare(s"select * from $userInfoByFacebookIdTable where facebookId = :fbId")
  private lazy val insertUserInfoByFacebookId = session.prepare(
    s"insert into $userInfoByFacebookIdTable (facebookId, seq, userInfo) values (:fbId, 1, :userInfo) if not exists")
  private lazy val updateUserInfoByFacebookId = session.prepare(
    s"update $userInfoByFacebookIdTable set userInfo = :userInfo, seq = :newSeq where facebookId = :fbId if seq = :oldSeq")

  override def persistUserEvent(userId: UUID, lastSeqNum: Option[Long], time: DateTime, event: UserEvent): Boolean = {
    val batch = new BatchStatement()

    val newMax = lastSeqNum.map(_ + 1).getOrElse(1L)
    val seqMaxQuery = lastSeqNum match {
      case Some(oldMax) ⇒ updateMaxSeq.bind().setLong("oldMax", oldMax).setLong("newMax", newMax)
      case None ⇒ insertMaxSeq.bind().setLong("newMax", newMax)
    }

    batch.add(seqMaxQuery.setUUID("userId", userId))

    batch.add(insertEvent.bind().
      setUUID("userId", userId).
      setLong("seq", newMax).
      setTimestamp("time", time.toDate).
      setBytes("event", ByteBuffer.wrap(event.asJson.noSpaces.getBytes)))
    session.execute(batch).wasApplied()
  }

  override def persistUserEvents(userId: UUID, lastSeqNum: Option[Long], time: DateTime, events: Seq[UserEvent]): Unit = {
    val batch = new BatchStatement()

    val newMax = lastSeqNum.map(_ + events.size).getOrElse(events.size.toLong)
    val seqMaxQuery = lastSeqNum match {
      case Some(oldMax) ⇒ updateMaxSeq.bind().setLong("oldMax", oldMax).setLong("newMax", newMax)
      case None ⇒ insertMaxSeq.bind().setLong("newMax", newMax)
    }

    batch.add(seqMaxQuery.setUUID("userId", userId))

    val offset = lastSeqNum.getOrElse(0L) + 1

    events.zipWithIndex.foreach { case (event, i) ⇒
      batch.add(insertEvent.bind().
        setUUID("userId", userId).
        setLong("seq", offset + i).
        setTimestamp("time", time.toDate).
        setBytes("event", ByteBuffer.wrap(event.asJson.noSpaces.getBytes)))
    }
    session.execute(batch)
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

  override def updateFacebookIdToUserInfo(facebookId: String, lastSeq: Option[Long], userInfo: UserInfo): Boolean = {
    lastSeq match {
      case Some(oldSeq) ⇒
        session.execute(updateUserInfoByFacebookId.bind().
          setString("fbId", facebookId).
          setLong("oldSeq", oldSeq).
          setLong("newSeq", oldSeq + 1).
          setBytes("userInfo", ByteBuffer.wrap(userInfo.asJson.noSpaces.getBytes()))).
          wasApplied()
      case None ⇒
        session.execute(insertUserInfoByFacebookId.bind().
          setString("fbId", facebookId).
          setBytes("userInfo", ByteBuffer.wrap(userInfo.asJson.noSpaces.getBytes()))).
          wasApplied()
    }
  }

  override def userInfoByFacebookId(facebookId: String): Option[UserInfoInstance] = {
    val result = session.execute(selectUserInfoByFacebookId.bind().setString("fbId", facebookId))
    if (result.getAvailableWithoutFetching > 0) {
      val row = result.one()
      val json = new String(row.getBytes("userInfo").array())
      val userInfoOrError = decode[UserInfo](json)
      userInfoOrError match {
        case Right(userInfo: UserInfo) ⇒ Option(UserInfoInstance(userInfo, row.getLong("seq")))
        case Left(err: Throwable) ⇒ throw err
        case Left(err) ⇒ throw new RuntimeException(s"Error decoding json: $json [${err.toString}]")
      }
    }
    else None
  }
}
