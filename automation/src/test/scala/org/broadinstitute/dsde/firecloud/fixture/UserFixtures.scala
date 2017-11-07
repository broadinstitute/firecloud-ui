package org.broadinstitute.dsde.firecloud.fixture

import org.broadinstitute.dsde.firecloud.config.{AuthToken, Config, Credentials}
import org.broadinstitute.dsde.firecloud.page.AuthenticatedPage
import org.broadinstitute.dsde.firecloud.page.user.{RegistrationPage, SignInPage}
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceListPage
import org.broadinstitute.dsde.firecloud.test.{CleanUp, WebBrowserSpec}
import org.openqa.selenium.WebDriver
import org.scalatest.Suite

trait UserFixtures extends CleanUp { self: WebBrowserSpec with Suite =>

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
      new SignInPage(Config.FireCloud.baseUrl).open
      executeScript(s"window.forceSignedIn('${AuthToken(user).value}')")
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
