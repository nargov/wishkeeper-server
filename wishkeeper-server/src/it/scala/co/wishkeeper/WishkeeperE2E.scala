package co.wishkeeper

import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit.SECONDS

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.{ActorMaterializer, Materializer}
import co.wishkeeper.server._
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.appium.java_client.remote.MobileCapabilityType._
import io.appium.java_client.remote.MobilePlatform.ANDROID
import io.appium.java_client.service.local.flags.GeneralServerFlag
import io.appium.java_client.service.local.{AppiumDriverLocalService, AppiumServiceBuilder}
import io.appium.java_client.{AppiumDriver, MobileElement}
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import org.openqa.selenium.remote.DesiredCapabilities
import org.scalatest._
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.{MatchResult, Matcher}

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.Try

class WishkeeperE2E extends AsyncFlatSpec with Matchers with BeforeAndAfterAll with Inside with Eventually
  with IntegrationPatience with ScalaFutures with OptionValues {
  implicit val system = ActorSystem("test-system")
  implicit val materializer = ActorMaterializer()
  override implicit val executionContext: ExecutionContext = system.dispatcher
  implicit val circeConfig = Configuration.default.withDefaults

  val appiumService = AppiumDriverLocalService.buildService(new AppiumServiceBuilder().withArgument(GeneralServerFlag.LOG_LEVEL, "warn"))
  val apkFile = new File("/home/nimrod/dev/wishkeeper-mobile/android/app/build/outputs/apk/app-release.apk")
  val capabilities = new DesiredCapabilities(
    Map(
      DEVICE_NAME → "Android Emulator",
      APP → apkFile.getAbsolutePath,
      PLATFORM_NAME → ANDROID
    ).asJava)
  var driver: AppiumDriver[MobileElement] = _

  val facebookTestHelper = new FacebookTestHelper()
  val dataStoreTestHelper = DataStoreTestHelper()

  val server = new WishkeeperServer()
  override protected def beforeAll(): Unit = {
    appiumService.start()
    driver = new AppiumDriver[MobileElement](capabilities)
    driver.manage().timeouts().implicitlyWait(5, SECONDS)

    CassandraDocker.start()
    dataStoreTestHelper.start()
    dataStoreTestHelper.createSchema()

    server.start()
  }

  it should "allow user to login with facebook account" in {
    val testUser = facebookTestHelper.createTestUser()


    driver.resetApp()
    driver.findElementByXPath("""//android.widget.TextView[@text="CONNECT WITH FACEBOOK"]""").click()
    driver.findElementByXPath("""//android.widget.EditText[@password="false"]""").setValue(testUser.email)
    driver.findElementByXPath("""//android.widget.EditText[@password="true"]""").setValue(testUser.password)
    driver.findElementByXPath("//android.widget.Button").click()
    driver.findElementByXPath("""//android.view.View[contains(@content-desc, 'Wishkeeper')]""")
    driver.findElementByXPath("""//android.widget.Button[@instance="1"]""").click()

    val userId = eventually {
      val response = Http().singleRequest(HttpRequest(uri = s"http://localhost:${WebApi.defaultManagementPort}/users/facebook/${testUser.id}"))
      whenReady(response) { res =>
        res should (beSuccessful and haveBody(isUUID))
        Await.result(Unmarshal(res.entity).to[UUID], 5.seconds)
      }
    }

    whenReady(facebookTestHelper.addAccessToken(testUser)) {testUserWithAccessToken =>

      val testUserProfile = facebookTestHelper.addUserDetails(testUserWithAccessToken).userProfile

      eventually {
        val response = Http().singleRequest(HttpRequest().withUri(s"http://localhost:${WebApi.defaultManagementPort}/users/$userId/profile"))
        whenReady(response) { res =>
          whenReady(Unmarshal(res.entity).to[UserProfile]) { profile =>
            inside(profile) { case UserProfile(_, _, birthday, email, _, _, name, gender, locale, _, _, _) =>
              birthday shouldBe testUserProfile.value.birthday
              email shouldBe testUserProfile.value.email
              name shouldBe testUserProfile.value.name
              gender shouldBe testUserProfile.value.gender
              locale shouldBe testUserProfile.value.locale
            }
          }
        }
      }
    }
  }

  override protected def afterAll(): Unit = {
    server.stop()
    appiumService.stop()
    facebookTestHelper.deleteTestUsers()
    dataStoreTestHelper.stop()
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

