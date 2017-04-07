package co.wishkeeper

import java.io.File
import java.util.concurrent.TimeUnit.SECONDS

import io.appium.java_client.remote.MobileCapabilityType.{APP, DEVICE_NAME, PLATFORM_NAME}
import io.appium.java_client.remote.MobilePlatform.ANDROID
import io.appium.java_client.service.local.AppiumDriverLocalService
import io.appium.java_client.{AppiumDriver, MobileElement}
import org.openqa.selenium.remote.DesiredCapabilities
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}

import scala.collection.JavaConverters._

class WishkeeperE2E extends FlatSpec with Matchers with BeforeAndAfter {

  val appiumService = AppiumDriverLocalService.buildDefaultService()
  appiumService.start()

  val apkFile = new File("/home/nimrod/dev/wishkeeper-mobile/android/app/build/outputs/apk/app-release.apk")
  val capabilities = new DesiredCapabilities(
    Map(
      DEVICE_NAME → "Android Emulator",
      APP → apkFile.getAbsolutePath,
      PLATFORM_NAME → ANDROID
    ).asJava)
  val driver = new AppiumDriver[MobileElement](capabilities)
  driver.manage().timeouts().implicitlyWait(5, SECONDS)

  "Wishkeeper app" should "present a login screen" in {
    driver.findElementByXPath("""//android.widget.TextView[@text="Wishkeeper"]""")
    driver.findElementByXPath("""//android.widget.TextView[@text="Find out what gifts your friends actually want"]""")
    driver.findElementByXPath("//android.widget.Button")
  }

  it should "allow user to login with facebook account" in {
    driver.resetApp()
    driver.findElementByXPath("//android.widget.Button").click()
    driver.findElementByXPath("""//android.widget.EditText[@password="false"]""").sendKeys("nimrod.argov@gmail.com")
    driver.findElementByXPath("""//android.widget.EditText[@password="true"]""").sendKeys("tatessectrcrn")
    driver.findElementByXPath("//android.widget.Button").click()
    driver.findElementByXPath("""//android.widget.Button[@content-desc="OK"]""").click()
  }
}