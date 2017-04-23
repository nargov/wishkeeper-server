package co.wishkeeper

import java.io.File
import java.util.concurrent.TimeUnit.SECONDS

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.{ActorMaterializer, Materializer}
import cats.syntax.either._
import co.wishkeeper.server.{CassandraDocker, FacebookData, Server, UserInfo}
import com.datastax.driver.core.{Cluster, Session}
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.appium.java_client.remote.MobileCapabilityType._
import io.appium.java_client.remote.MobilePlatform.ANDROID
import io.appium.java_client.service.local.flags.GeneralServerFlag
import io.appium.java_client.service.local.{AppiumDriverLocalService, AppiumServiceBuilder}
import io.appium.java_client.{AppiumDriver, MobileElement}
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import org.openqa.selenium.remote.DesiredCapabilities
import org.scalatest._
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.exceptions.TestFailedException
import org.scalatest.matchers.{HavePropertyMatchResult, HavePropertyMatcher, MatchResult, Matcher}

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class WishkeeperE2E extends AsyncFlatSpec with Matchers with BeforeAndAfterAll with Inside with Eventually with IntegrationPatience with ScalaFutures {
  implicit val system = ActorSystem("test-system")
  implicit val materializer = ActorMaterializer()

  val cluster = Cluster.builder().addContactPoint("localhost").build()
  var session: Session = _

  val appiumService = AppiumDriverLocalService.buildService(new AppiumServiceBuilder().withArgument(GeneralServerFlag.LOG_LEVEL, "warn"))
  val apkFile = new File("/home/nimrod/dev/wishkeeper-mobile/android/app/build/outputs/apk/app-release.apk")
  val capabilities = new DesiredCapabilities(
    Map(
      DEVICE_NAME → "Android Emulator",
      APP → apkFile.getAbsolutePath,
      PLATFORM_NAME → ANDROID
    ).asJava)
  var driver: AppiumDriver[MobileElement] = _

  override protected def beforeAll(): Unit = {
    appiumService.start()
    driver = new AppiumDriver[MobileElement](capabilities)
    driver.manage().timeouts().implicitlyWait(5, SECONDS)

    CassandraDocker.start()
    session = cluster.connect()

    session.execute("create keyspace if not exists wishkeeper with replication = {'class': 'SimpleStrategy', 'replication_factor': 1}")
      .wasApplied() shouldBe true

    session.execute(
      """
        |create table if not exists wishkeeper.user_events(
        | userId UUID,
        | seq bigint,
        | seqMax bigint STATIC,
        | time timestamp,
        | event blob,
        | PRIMARY KEY((userId), seq)
        |)
      """.stripMargin).wasApplied() shouldBe true

    session.execute(
      """
        |create table if not exists wishkeeper.user_info_by_facebook_id(
        | facebookId text,
        | seq bigint,
        | userInfo blob,
        | PRIMARY KEY(facebookId)
        |)
      """.stripMargin
    )

  }


  it should "allow user to login with facebook account" in {
    val fbAppId = "1376924702342472"
    val fbAppSecret = "3f5ee9ef27bd152217246ab02bed5725"
    val access_token = s"$fbAppId|$fbAppSecret"
    val eventualTestUserResponse = Http().singleRequest(HttpRequest().
      withMethod(HttpMethods.POST).
      withUri(s"https://graph.facebook.com/v2.8/$fbAppId/accounts/test-users").
      withEntity(s"access_token=$access_token&installed=false"))
    val testUserResponse = Await.result(eventualTestUserResponse, 20.seconds)
    val eventualJson: Future[String] = testUserResponse.entity.dataBytes.runFold("")(_ + _.utf8String)
    val json = Await.result(eventualJson, 5.seconds)
    val doc = parse(json).getOrElse(Json.Null)
    val cursor = doc.hcursor
    val testUserId = extractTestUserProperty(cursor, "id")
    val testUserEmail = extractTestUserProperty(cursor, "email")
    val testUserPassword = extractTestUserProperty(cursor, "password")

    val port = Server.defaultPort
    Server.start(port)

    driver.resetApp()
    driver.findElementByXPath("""//android.widget.TextView[@text="CONNECT WITH FACEBOOK"]""").click()
    driver.findElementByXPath("""//android.widget.EditText[@password="false"]""").setValue(testUserEmail)
    driver.findElementByXPath("""//android.widget.EditText[@password="true"]""").setValue(testUserPassword)
    driver.findElementByXPath("//android.widget.Button").click()
    driver.findElementByXPath("""//android.view.View[contains(@content-desc, 'Wishkeeper')]""")
    driver.findElementByXPath("""//android.widget.Button[@instance="1"]""").click()

    def facebookId(id: String) = new HavePropertyMatcher[FacebookData, String] {
      override def apply(facebookData: FacebookData) = new HavePropertyMatchResult(facebookData.id == id, "id", id, facebookData.id)
    }

    def isUserInfoWith(id: String) = have(facebookId(id)) compose { (userInfo: UserInfo) => userInfo.facebookData.get }

    eventually {
      val response = Http().singleRequest(HttpRequest(uri = s"http://localhost:$port/users/facebook/$testUserId"))
      whenReady(response) { res =>
        res should (beSuccessful and haveJsonEntityThat(isUserInfoWith(testUserId)))
      }
    }
  }

  private def extractTestUserProperty(cursor: HCursor, property: String) = {
    cursor.get[String](property).getOrElse(throw new TestFailedException(Some("Failed to create facebook test user"), None, 0))
  }

  override protected def afterAll(): Unit = {
    appiumService.stop()
    system.terminate()
  }

  def beSuccessful: Matcher[HttpResponse] = Matcher { (response: HttpResponse) ⇒
    MatchResult(
      response.status == StatusCodes.OK,
      s"Unexpected response status - expected [${StatusCodes.OK}] but was [${response.status}]",
      s"Unexpected response status - expected Not [${StatusCodes.OK}] but was [${StatusCodes.OK}]"
    )
  }

  def haveJsonEntityThat[T](matcher: Matcher[T])
                           (implicit um: Unmarshaller[ResponseEntity, T], ec: ExecutionContext, mat: Materializer): Matcher[HttpResponse] = {
    Matcher { response: HttpResponse =>
      whenReady(Unmarshal(response.entity).to[T](um, ec, mat)) { entity =>
        matcher.apply(entity)
      }
    }
  }
}