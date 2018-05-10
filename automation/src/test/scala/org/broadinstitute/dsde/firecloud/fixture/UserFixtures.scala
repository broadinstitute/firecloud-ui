package org.broadinstitute.dsde.firecloud.fixture

import org.scalatest.concurrent.{Eventually, ScaledTimeSpans}
import org.broadinstitute.dsde.firecloud.page.AuthenticatedPage
import org.broadinstitute.dsde.firecloud.page.user.{SignInPage, RegistrationPage}
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceListPage
import org.broadinstitute.dsde.workbench.config.{Credentials, Config}
import org.broadinstitute.dsde.workbench.service.test.{WebBrowserSpec, CleanUp}
import org.openqa.selenium.WebDriver
import org.scalatest.TestSuite
import org.broadinstitute.dsde.workbench.service.util.Retry.retry
import scala.concurrent.duration._

trait UserFixtures extends CleanUp with ScaledTimeSpans with Eventually { self: WebBrowserSpec with TestSuite =>

  /**
    * "Signs in" to FireCloud with an access token, bypassing the Google sign-in flow. Assumes the
    * user is already registered so hands the test code a ready WorkspaceListPage.
    */
  def withSignIn(user: Credentials)
                (testCode: (WorkspaceListPage) => Any)(implicit webDriver: WebDriver): Unit = {
    withSignIn(user, new WorkspaceListPage)(testCode)
  }

  /**
    * Signs in to FireCloud using the Google sign-in flow. Assumes the user is already registered so
    * hands the test code a ready WorkspaceListPage.
    */
  def withSignInReal(user: Credentials)
                    (testCode: (WorkspaceListPage) => Any)(implicit webDriver: WebDriver): Unit = {
    withSignInReal(user, new WorkspaceListPage)(testCode)
  }

  /**
    * Signs in to FireCloud using the Google sign-in flow. Returns a ready RegistrationPage.
    */
  def withSignInNewUserReal(user: Credentials)
                           (testCode: (RegistrationPage) => Any)(implicit webDriver: WebDriver): Unit = {
    withSignInReal(user, new RegistrationPage)(testCode)
  }


  private def withSignIn[T <: AuthenticatedPage](user: Credentials, page: T)
                                                (testCode: (T) => Any)
                                                (implicit webDriver: WebDriver): Unit = {
    withSignIn(user, {
      val openedPage: SignInPage = new SignInPage(Config.FireCloud.baseUrl).open
      // One way to workaround flaky SignIn issue
      var counter = 0
      retry(Seq.fill(2)(5.seconds)) ({
        executeScript(s"window.forceSignedIn('${user.makeAuthToken().value}')")
        if (counter > 0) logger.warn(s"Retried forceSignedIn. $counter") // how many times has retried. log to be removed
        counter +=1
        try {
          page.awaitReady() // page could be take a short while to load
          Some(page)
        } catch {
          case _: Throwable => None
        }
      })
    }, page, testCode)
  }

  private def withSignInReal[T <: AuthenticatedPage](user: Credentials, page: T)
                                                    (testCode: (T) => Any)
                                                    (implicit webDriver: WebDriver): Unit = {
    withSignIn(user, {
      new SignInPage(Config.FireCloud.baseUrl).open.signIn(user.email, user.password)
    }, page, testCode)
  }

  private def withSignIn[T <: AuthenticatedPage](user: Credentials, signIn: => Unit,
                                                         page: T, testCode: (T) => Any)
                                                        (implicit webDriver: WebDriver): Unit = {
    signIn
    await ready page

    // Don't try/finally here to prevent sign-out before capturing a failure screenshot.
    testCode(page)

    try {
      page.signOut()
    } catch nonFatalAndLog(s"Error logging out user: ${user.email}")
  }
}
