package org.broadinstitute.dsde.firecloud.pages

import java.io.File
import java.net.URL
import java.util.UUID

import org.broadinstitute.dsde.firecloud.api.Orchestration
import org.broadinstitute.dsde.firecloud.{Config, WebBrowserUtil}
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriverService
import org.openqa.selenium.remote.{DesiredCapabilities, LocalFileDetector, RemoteWebDriver}

import scala.sys.SystemProperties
import scala.util.Random

/**
  * Base spec for writing FireCloud web browser tests.
  */
trait WebBrowserSpec extends WebBrowserUtil {

  val api = Orchestration

  /**
    * Executes a test in a fixture with a managed WebDriver. A test that uses
    * this will get its own WebDriver instance will be destroyed when the test
    * is complete. This encourages test case isolation.
    *
    * @param testCode the test code to run
    */
  def withWebDriver(testCode: (WebDriver) => Any): Unit = {
    //this needs to be changed!
    val service = new ChromeDriverService.Builder().usingDriverExecutable(new File("/usr/local/bin/chromedriver")).usingAnyFreePort().build()
    service.start()
    implicit val webDriver = initWebDriver(service)
    try {
      testCode(webDriver)
    } finally {
      webDriver.quit()
      service.stop()
    }
  }

  private def initWebDriver(service: ChromeDriverService): WebDriver = {
    val localBrowser = new SystemProperties().get("local.browser")
    val defaultChrome = Config.ChromeSettings.chromedriverHost
    val driver = localBrowser match {
      case Some("true") => new RemoteWebDriver(service.getUrl, DesiredCapabilities.chrome())
      case _ => new RemoteWebDriver(new URL(defaultChrome), DesiredCapabilities.chrome())
    }
    driver.setFileDetector(new LocalFileDetector())
    driver
  }

  /**
    * Make a random alpha-numeric (lowercase) string to be used as a semi-unique
    * identifier.
    *
    * @param length the number of characters in the string
    * @return a random string
    */
  def makeRandomId(length: Int = 7): String = {
    Random.alphanumeric.take(length).mkString.toLowerCase
  }

  def randomUuid: String = {
    UUID.randomUUID().toString
  }

  /**
    * Convenience method for sign-in to the configured FireCloud URL.
    */
  def signIn(email: String, password: String)(implicit webDriver: WebDriver): Unit = {
    new SignInPage(Config.FireCloud.baseUrl).open.signIn(email, password)
  }

  /**
    * Convenience method for sign-in to the configured FireCloud URL. Assumes
    * that the user has previously registered and will therefore be taken to
    * the workspace list page.
    */
  def signIn(credentials: Config.Credentials)(implicit webDriver: WebDriver): WorkspaceListPage = {
    signIn(credentials.email, credentials.password)
    new WorkspaceListPage
  }
}
