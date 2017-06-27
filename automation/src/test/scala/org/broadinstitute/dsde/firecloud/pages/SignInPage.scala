package org.broadinstitute.dsde.firecloud.pages

import org.broadinstitute.dsde.firecloud.{PageUtil, WebBrowserUtil}
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.{Page, WebBrowser}

/**
  * Page class for the page displayed when accessing FireCloud when not signed in.
  */
class SignInPage(val baseUrl: String)(implicit webDriver: WebDriver) extends FireCloudView with Page with PageUtil[SignInPage] {

  override val url: String = baseUrl

  /**
    * Sign in to FireCloud. Returns when control is handed back to FireCloud after Google sign-in is done.
    */
  def signIn(email: String, password: String): Unit = {
    val popup = beginSignIn()
    popup.signIn(email, password)
    await enabled testId("account-dropdown")
  }

  /**
    * Handles the pre-sign-in dance of switching Selenium's focus to Google's
    * sign-in pop-up window.
    *
    * @return a new GoogleSignInPopup
    */
  private def beginSignIn(): GoogleSignInPopup = {
    val initialWindowHandles = windowHandles

    gestures.clickSignIn()
    await condition (windowHandles.size > 1)

    val popupWindowHandle = (windowHandles -- initialWindowHandles).head

    switch to window(popupWindowHandle)
    new GoogleSignInPopup
  }

  object gestures {

    private val signInButton = testId("sign-in-button")

    def clickSignIn(): Unit = {
      click on (await enabled signInButton)
    }
  }
}

class GoogleSignInPopup(implicit webDriver: WebDriver) extends WebBrowser with WebBrowserUtil {

  /**
    * Signs in to Google to authenticate for FireCloud.
    */
  def signIn(email: String, password: String): Unit = {
    val chooseAccount = find(id("identifierLink")).filter(_.isDisplayed) foreach { click on _ }

    await enabled id("identifierId")
    emailField(id("identifierId")).value = email
    pressKeys("\n")

    await enabled name("password")
    pwdField(name("password")).value = password
    pressKeys("\n")

    returnFromSignIn()
  }

  /**
    * Handles the post-sign-in dance of switching Selenium's focus back to the
    * main FireCloud window.
    * TODO: make this work when there is more than one window
    */
  def returnFromSignIn(): Unit = {
    await condition (windowHandles.size == 1 || enabled(id("submit_approve_access")))
    if (windowHandles.size > 1) {
      click on id("submit_approve_access")
      await condition(windowHandles.size == 1)
    }

    switch to window(windowHandles.head)
  }
}