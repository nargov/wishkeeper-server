package co.wishkeeper

import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit.SECONDS

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.{ActorMaterializer, Materializer}
import cats.syntax.either._
import co.wishkeeper.server._
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.appium.java_client.remote.MobileCapabilityType._
import io.appium.java_client.remote.MobilePlatform.ANDROID
import io.appium.java_client.service.local.flags.GeneralServerFlag
import io.appium.java_client.service.local.{AppiumDriverLocalService, AppiumServiceBuilder}
import io.appium.java_client.{AppiumDriver, MobileElement}
import io.circe._
import io.circe.parser._
import io.circe.generic.auto._
import org.openqa.selenium.remote.DesiredCapabilities
import org.scalatest._
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.exceptions.TestFailedException
import org.scalatest.matchers.{MatchResult, Matcher}

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

class WishkeeperE2E extends AsyncFlatSpec with Matchers with BeforeAndAfterAll with Inside with Eventually with IntegrationPatience with ScalaFutures {
  implicit val system = ActorSystem("test-system")
  implicit val materializer = ActorMaterializer()

  val appiumService = AppiumDriverLocalService.buildService(new AppiumServiceBuilder().withArgument(GeneralServerFlag.LOG_LEVEL, "warn"))
  val apkFile = new File("/home/nimrod/dev/wishkeeper-mobile/android/app/build/outputs/apk/app-release.apk")
  val capabilities = new DesiredCapabilities(
    Map(
      DEVICE_NAME → "Android Emulator",
      APP → apkFile.getAbsolutePath,
      PLATFORM_NAME → ANDROID
    ).asJava)
  var driver: AppiumDriver[MobileElement] = _

  val fbAppId = "1376924702342472"
  val fbAppSecret = "3f5ee9ef27bd152217246ab02bed5725"
  val access_token = s"$fbAppId|$fbAppSecret"

  var testUserId: String = _

  override protected def beforeAll(): Unit = {
    appiumService.start()
    driver = new AppiumDriver[MobileElement](capabilities)
    driver.manage().timeouts().implicitlyWait(5, SECONDS)

    CassandraDocker.start()
    DataStoreTestHelper().createSchema()
  }


  it should "allow user to login with facebook account" in {
    val eventualTestUserResponse = Http().singleRequest(HttpRequest().
      withMethod(HttpMethods.POST).
      withUri(s"https://graph.facebook.com/v2.9/$fbAppId/accounts/test-users").
      withEntity(s"access_token=$access_token&installed=false"))
    val testUserResponse = Await.result(eventualTestUserResponse, 20.seconds)
    val eventualJson: Future[String] = testUserResponse.entity.dataBytes.runFold("")(_ + _.utf8String)
    val json = Await.result(eventualJson, 10.seconds)
    val doc = parse(json).getOrElse(Json.Null)
    val cursor = doc.hcursor
    testUserId = extractTestUserProperty(cursor, "id")
    val testUserEmail = extractTestUserProperty(cursor, "email")
    val testUserPassword = extractTestUserProperty(cursor, "password")

    new WishkeeperServer().start()

    driver.resetApp()
    driver.findElementByXPath("""//android.widget.TextView[@text="CONNECT WITH FACEBOOK"]""").click()
    driver.findElementByXPath("""//android.widget.EditText[@password="false"]""").setValue(testUserEmail)
    driver.findElementByXPath("""//android.widget.EditText[@password="true"]""").setValue(testUserPassword)
    driver.findElementByXPath("//android.widget.Button").click()
    driver.findElementByXPath("""//android.view.View[contains(@content-desc, 'Wishkeeper')]""")
    driver.findElementByXPath("""//android.widget.Button[@instance="1"]""").click()

    val userId = eventually {
      val response = Http().singleRequest(HttpRequest(uri = s"http://localhost:${WebApi.defaultManagementPort}/users/facebook/$testUserId"))
      whenReady(response) { res =>
        res should (beSuccessful and haveBody(isUUID))
        Await.result(Unmarshal(res.entity).to[UUID], 5.seconds)
      }
    }

    val eventualTestUsers = Http().singleRequest(HttpRequest().
      withUri(s"https://graph.facebook.com/v2.9/$fbAppId/accounts/test-users?access_token=$access_token"))
    val testUsersResponse = Await.result(eventualTestUsers, 20.seconds)
    val eventualTestUsersJson = testUsersResponse.entity.dataBytes.runFold("")(_ + _.utf8String)
    val testUsersJson = Await.result(eventualTestUsersJson, 20.seconds)
    println(testUsersJson)
    val testUsers = parse(testUsersJson).getOrElse(Json.Null)
    val testUsersCursor = testUsers.hcursor
    val testUserAccessToken = testUsersCursor.downField("data").
      downAt(_.hcursor.get[String]("id").right.get == testUserId).
      get[String]("access_token").right.get
    println(s"access token for test user: $testUserAccessToken")
    val testUserDetailsResponse = Await.result(Http().singleRequest(HttpRequest().
      withUri(s"https://graph.facebook.com/v2.9/$testUserId?access_token=$testUserAccessToken&fields=name,birthday,email,gender,locale")), 10.seconds)
    val testUserDetails = Await.result(testUserDetailsResponse.entity.dataBytes.runFold("")(_ + _.utf8String), 10.seconds)
    val testUserProfile = decode[UserProfile](testUserDetails).right.get
    println(testUserProfile)


    eventually {
      val response = Http().singleRequest(HttpRequest().withUri(s"http://localhost:${WebApi.defaultManagementPort}/users/$userId/profile"))
      whenReady(response) { res =>
        whenReady(Unmarshal(res.entity).to[UserProfile]) { profile =>
          inside(profile) { case UserProfile(_, _, birthday, email, _, _, name, gender, locale, _) =>
            birthday shouldBe testUserProfile.birthday
            email shouldBe testUserProfile.email
            name shouldBe testUserProfile.name
            gender shouldBe testUserProfile.gender
            locale shouldBe testUserProfile.locale
          }
        }
      }
    }

    succeed
  }

  private def extractTestUserProperty(cursor: HCursor, property: String) = {
    cursor.get[String](property).getOrElse(throw new TestFailedException(Some("Failed to create facebook test user"), None, 0))
  }

  override protected def afterAll(): Unit = {
    appiumService.stop()
    if (testUserId != null) {
      val deleteResponse = Http().singleRequest(
        HttpRequest().withMethod(HttpMethods.DELETE).withUri(s"https://graph.facebook.com/v2.9/$testUserId").
          withEntity(s"access_token=$access_token"))
      Await.ready(deleteResponse, 10.seconds)
    }
    system.terminate()
  }

  def beSuccessful: Matcher[HttpResponse] = Matcher { (response: HttpResponse) ⇒
    MatchResult(
      response.status == StatusCodes.OK,
      s"Unexpected response status - expected [${StatusCodes.OK}] but was [${response.status}]",
      s"Unexpected response status - expected Not [${StatusCodes.OK}] but was [${StatusCodes.OK}]"
    )
  }

  def haveBody[T](matcher: Matcher[T])
                 (implicit um: Unmarshaller[ResponseEntity, T], ec: ExecutionContext, mat: Materializer): Matcher[HttpResponse] =
    Matcher { response: HttpResponse =>
      whenReady(Unmarshal(response.entity).to[T](um, ec, mat)) { body =>
        matcher.apply(body)
      }
    }

  def isUUID: Matcher[String] = Matcher { str =>
    MatchResult(Try(UUID.fromString(str)).isSuccess,
      s"$str in not a UUID",
      s"$str is a UUID")
  }
}

