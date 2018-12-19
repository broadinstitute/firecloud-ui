package org.broadinstitute.dsde.firecloud.page.user

import org.broadinstitute.dsde.firecloud.FireCloudView
import org.broadinstitute.dsde.firecloud.component._
import org.broadinstitute.dsde.firecloud.page.PageUtil
import org.broadinstitute.dsde.workbench.service.test.WebBrowserUtil
import org.openqa.selenium.support.ui.{ExpectedConditions, WebDriverWait}
import org.openqa.selenium.{TimeoutException, WebDriver}
import org.scalatest.selenium.{Page, WebBrowser}

import scala.util.{Failure, Success, Try}

/**
  * Page class for the page displayed when accessing FireCloud when not signed in.
  */
class SignInPage(val baseUrl: String)(implicit webDriver: WebDriver) extends FireCloudView with Page with PageUtil[SignInPage] {

  case class GoogleSignInButton(queryString: CSSQuery)(implicit webDriver: WebDriver) extends Component(queryString) with Clickable {
    override def awaitReady(): Unit = {
      val signInTextXpath = s"${queryString.text} span"
      log.info(s"GoogleSignInButton starting ready-wait for $signInTextXpath ...")
      await condition {
        val signInText = findAll(cssSelector(signInTextXpath))
        signInText.exists(_.text.contains("Sign in with Google"))
      }
    }
  }

  override def awaitReady(): Unit = {
    log.info("SignInPage.awaitReady starting to wait for signInButton ... ")

    Try (signInButton awaitReady()) match {
      case Success(_) =>
        log.info("SignInPage.awaitReady believes signInButton is ready; sleeping for 500ms  ... ")

        /*
         * The FireCloud not-signed-in page renders the sign-in button while it is still doing some
         * initialization. If you log the status of the App components state for user-status and auth2
         * with each render, you see the following sequence:
         *   '#{}' ''
         *   '#{}' '[object Object]'
         *   '#{:refresh-token-saved}' '[object Object]'
         * If the page is used before this is complete (for example window.forceSignedIn adding
         * :signed-in to user-status), bad things happen (for example :signed-in being dropped from
         * user-status). Instead of reworking the sign-in logic for a case that (for the most part) only
         * a computer will operate fast enough to encounter, we'll just slow the computer down a little.
         */
        Thread.sleep(500)
      case Failure(f) =>
        log.error(s"SignInPage timed out waiting for signInButton to be ready: ${f.getMessage}")
        throw(f)
    }
  }

  override val url: String = baseUrl

  private val signInButton = GoogleSignInButton(CSSQuery("#sign-in-button"))

  def isOpen = signInButton.isVisible

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

    signInButton.doClick()
    await condition (windowHandles.size == 2)

    val popupWindowHandle = (windowHandles -- initialWindowHandles).head

    switch to window(popupWindowHandle)
    new GoogleSignInPopup().awaitLoaded()
  }
}



class GoogleSignInPopup(implicit webDriver: WebDriver) extends WebBrowser with WebBrowserUtil {

  def awaitLoaded(): GoogleSignInPopup = {
    Try {
      await condition (id("identifierLink").findElement.exists(_.isDisplayed)
        || id("identifierId").findElement.exists(_.isEnabled)
        || id("Email").findElement.exists(_.isDisplayed), 10) // One Account All of Google popup
    } match {
      case Success(_) =>
        find(id("identifierLink")) foreach { link =>
          click on link
          await visible id("identifierId")
        }
      case Failure(t) => throw new TimeoutException("Timed out (10 seconds) waiting for Google SignIn Popup.", t)
    }

    this
  }

  /**
    * Signs in to Google to authenticate for FireCloud.
    */
  def signIn(email: String, password: String): Unit = {
    (id("Email").findElement.isDefined) match {
      case true => oneAccountSignIn(email, password)
      case false => normalSignIn(email, password)
    }
    returnFromSignIn()
  }

  private def normalSignIn(email: String, password: String): Unit = {
    await enabled id("identifierId")
    emailField(id("identifierId")).value = email
    pressKeys("\n")

    await enabled id("passwordNext")
    await enabled name("password")
    /*
     * The Google real SignIn: animation tranisition from username to password freezes when other web browsers are in front thus blocking animation.
     * Wait up to 60 seconds for animation finish.
     */
    // Thread sleep 1000
    await condition (find(id("password")).exists(_.isDisplayed), 60)
    pwdField(name("password")).value = password
    pressKeys("\n")
  }

  private def oneAccountSignIn(email: String, password: String): Unit = {
    emailField(id("email")).value = email
    find(id("next")).get.underlying.submit()
    pwdField(id("Passwd")).value = password
    find(id("signIn")).get.underlying.submit()
  }

  /**
    * Handles the post-sign-in dance of switching Selenium's focus back to the
    * main FireCloud window.
    * TODO: make this work when there is more than one window
    */
  def returnFromSignIn(): Unit = {
    /*
     * The sign-in popup may go away at any time which could cause any calls
     * such as findElement to fail with NullPointerException. Therefore, the
     * only safe check we can make is on the number of windows.
     */
    try {
      await condition (windowHandles.size == 1)
    } catch {
      case _: TimeoutException =>
        /*
         * If there is still more than 1 window after 30 seconds, we most likely
         * need to approve access to continue.
         */
        if (windowHandles.size > 1) {
          click on id("submit_approve_access")
          await condition(windowHandles.size == 1)
        }
    }

    switch to window(windowHandles.head)
  }
}
