package co.wishkeeper.server.events.processing

import java.io.InputStream
import java.util.UUID
import java.util.UUID.randomUUID

import co.wishkeeper.server.Events.{Event, UserPictureSet}
import co.wishkeeper.server.{DataStore, FileAdapter}
import co.wishkeeper.server.image.{ImageData, ImageStore}
import com.wixpress.common.specs2.JMock
import org.joda.time.DateTime
import org.specs2.matcher.Matcher
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import scala.util.{Success, Try}


class ImageUploadEventProcessorTest extends Specification with JMock {
  "Should upload image if profile picture url prefix is not from image repository" in new Context {


    checking {
      ignoring(dataStore)
      allowing(fileAdapter).inputStreamFor(imageUrl).willReturn(Success(inputStream))
      allowing(imageStore).imageLinkBase.willReturn(linkBase)
      oneOf(imageStore).save(having(===(ImageData(inputStream, "image/png"))), having(aUUID))
    }

    val events = processor.process(UserPictureSet(userId, imageUrl), userId)
    events must contain(aUserPictureSetTupleEventWith(userId, linkBase))
  }

  "should ignore event if picture url prefix is from image repository" in new Context {
    checking {
      allowing(imageStore).imageLinkBase.willReturn(linkBase)
    }

    processor.process(UserPictureSet(userId, linkBase + "some-image"), userId) must beEmpty
  }

  "should save new picture url in profile" in new Context {
    checking {
      allowing(imageStore).imageLinkBase.willReturn(linkBase)
      ignoring(imageStore).save(having(any), having(any))
      allowing(fileAdapter).inputStreamFor(imageUrl).willReturn(Success(inputStream))
      allowing(dataStore).lastSequenceNum(userId).willReturn(Option(3L))
      oneOf(dataStore).saveUserEvents(having(===(userId)), having(beSome[Long]), having(any[DateTime]),
        having(contain(exactly(aUserPictureSetEventWith(userId, linkBase)))))
    }

    processor.process(UserPictureSet(userId, imageUrl), userId)
  }

  trait Context extends Scope {
    val imageStore = mock[ImageStore]
    val fileAdapter = mock[FileAdapter]
    val dataStore = mock[DataStore]
    val processor = new ImageUploadEventProcessor(imageStore, fileAdapter, dataStore)
    val userId = randomUUID()
    val imageUrl = "http://some.website.com/somepic.png"
    val linkBase = "http://example.com/"
    val inputStream = new InputStream {
      override def read(): Int = 0
    }
  }

  def aUUID: Matcher[String] = (str:String) => (Try{ UUID.fromString(str) }.isSuccess, s"$str is not a UUID")

  def aUserPictureSetEventWith(userId: UUID, prefix: String): Matcher[Event] = (e:Event) => e match {
    case UserPictureSet(id, link) => (userId == id && link.startsWith(prefix), "UserPictureSet event does not have required userId or prefix")
  }

  def aUserPictureSetTupleEventWith(userId: UUID, prefix: String): Matcher[(UUID, Event)] = (t:(UUID,Event)) => t match {
    case (_, UserPictureSet(id, link)) => (userId == id && link.startsWith(prefix), "UserPictureSet event does not have required userId or prefix")
    case _ => (false, "Not a UserPictureSet event")
  }
}
