package org.broadinstitute.dsde.firecloud.page

import org.broadinstitute.dsde.firecloud.FireCloudView
import org.broadinstitute.dsde.firecloud.component.{Link, TestId}
import org.openqa.selenium.{Keys, TimeoutException, WebDriver}
import org.openqa.selenium.interactions.Actions
import org.scalatest.selenium.Page


/**
  * Base class for pages that are reachable after signing in.
  */
abstract class AuthenticatedPage(implicit webDriver: WebDriver) extends FireCloudView with Page {

  override def awaitReady(): Unit = {
    try { await notVisible (cssSelector("[data-test-id=spinner]"), 60) } catch {
      case _: TimeoutException =>
        throw new TimeoutException(s"Timed out waiting for Spinner stop on page $url. Continuing anyway. Opened url is ${webDriver.getCurrentUrl}.")
    }

    val actualUrl = Option(webDriver.getCurrentUrl).getOrElse("").trim.replaceAll(" ", "%20")
    val expectUrl = Option(url).getOrElse("").trim.replaceAll(" ", "%20")
    if (!expectUrl.startsWith(actualUrl)) {
      throw new TimeoutException(s"Failed awaitReady() because URL mismatch. Opened URL is $actualUrl. Expected URL is $expectUrl")
    }
  }

  /**
    * Sign out of FireCloud.
    */
  def signOut(): Unit = {
    ui.clickAccountDropdown()
    ui.clickSignOut()
  }

  def readUserEmail(): String = {
    ui.readUserEmail()
  }

  /**
    * Press ESP key to close open Modal
    */
  def closeModal(): Unit = {
    val modal = find(CssSelectorQuery("body.broadinstitute-modal-open"))
    modal.foreach(_ => new Actions(webDriver).sendKeys(Keys.ESCAPE).perform())
  }

  trait UI {
    private val accountDropdown = TestId("account-dropdown")
    private val accountDropdownEmail = testId("account-dropdown-email")
    private val signOutLink = TestId("sign-out")

    def clickAccountDropdown(): Unit = {
      Link(accountDropdown).doClick()
    }

    def clickSignOut(): Unit = {
      Link(signOutLink).doClick()
    }

    def readUserEmail(): String = {
      await enabled accountDropdownEmail
      find(accountDropdownEmail).get.text
    }
  }

  /*
   * This must be private so that subclasses can provide their own object
   * named "ui". The only disadvantage is that subclasses that want one MUST
   * provide their own "ui" object. However, it should be very rare that a
   * page class will want a "ui" object without also providing an extension of
   * the AuthenticatedPage.UI trait.
   */
  private object ui extends UI
}
