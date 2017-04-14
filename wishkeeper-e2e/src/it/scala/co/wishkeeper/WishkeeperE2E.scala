package co.wishkeeper

import java.io.File
import java.util.concurrent.TimeUnit.SECONDS

import io.appium.java_client.remote.MobileCapabilityType.{APP, DEVICE_NAME, PLATFORM_NAME}
import io.appium.java_client.remote.MobilePlatform.ANDROID
import io.appium.java_client.service.local.flags.GeneralServerFlag
import io.appium.java_client.service.local.{AppiumDriverLocalService, AppiumServiceBuilder}
import io.appium.java_client.{AppiumDriver, MobileElement}
import org.openqa.selenium.remote.DesiredCapabilities
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

import scala.collection.JavaConverters._

class WishkeeperE2E extends FlatSpec with Matchers with BeforeAndAfterAll {

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
  }


  "Wishkeeper app" should "present a login screen" in {
    driver.findElementByXPath("""//android.widget.TextView[@text[starts-with(., "Give your friends")]]""")
    driver.findElementByXPath("""//android.widget.TextView[@text="CONNECT WITH FACEBOOK"]""")
  }

  it should "allow user to login with facebook account" in {
    driver.resetApp()
    driver.findElementByXPath("""//android.widget.TextView[@text="CONNECT WITH FACEBOOK"]""").click()
    driver.findElementByXPath("""//android.widget.EditText[@password="false"]""").setValue("nimrod.argov@gmail.com")
    driver.findElementByXPath("""//android.widget.EditText[@password="true"]""").setValue("tatessectrcrn")
    driver.findElementByXPath("//android.widget.Button").click()
    driver.findElementByXPath("""//android.widget.Button[@content-desc="OK"]""").click()
  }

  override protected def afterAll(): Unit = {
    appiumService.stop()
  }
}