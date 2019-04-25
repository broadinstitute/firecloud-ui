package org.broadinstitute.dsde.firecloud.fixture


import org.broadinstitute.dsde.firecloud.FireCloudConfig
import org.scalatest.concurrent.{Eventually, ScaledTimeSpans}
import org.broadinstitute.dsde.firecloud.page.AuthenticatedPage
import org.broadinstitute.dsde.firecloud.page.user.{RegistrationPage, SignInPage, TermsOfServicePage}
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceListPage
import org.broadinstitute.dsde.workbench.auth.AuthTokenScopes
import org.broadinstitute.dsde.workbench.config.Credentials
import org.broadinstitute.dsde.workbench.service.test.{CleanUp, WebBrowserSpec}
import org.openqa.selenium.WebDriver
import org.scalatest.TestSuite
import org.broadinstitute.dsde.workbench.service.util.Retry.retry

import scala.concurrent.duration._
import scala.util.Try

trait UserFixtures extends CleanUp with ScaledTimeSpans with Eventually { self: WebBrowserSpec with TestSuite =>

  /**
    * "Signs in" to FireCloud with an access token, bypassing the Google sign-in flow. Assumes the
    * user is already registered so hands the test code a ready WorkspaceListPage.
    */
  def withSignIn(user: Credentials)
                (testCode: WorkspaceListPage => Any)(implicit webDriver: WebDriver): Unit = {
    withSignIn(user, new WorkspaceListPage)(testCode)
  }

  /**
    * "Signs in" to FireCloud with an access token and the specified scopes, bypassing the Google sign-in flow.
    * Assumes the user is already registered so hands the test code a ready WorkspaceListPage.
    */
  def withScopedSignIn(user: Credentials, scopes: Seq[String])
                 (testCode: WorkspaceListPage => Any)(implicit webDriver: WebDriver): Unit = {
    withSignIn(user, new WorkspaceListPage, scopes)(testCode)
  }

  /**
    * Signs in to FireCloud using the Google sign-in flow. Assumes the user is already registered so
    * hands the test code a ready WorkspaceListPage.
    */
  def withSignInReal(user: Credentials)
                    (testCode: WorkspaceListPage => Any)(implicit webDriver: WebDriver): Unit = {
    withSignInReal(user, new WorkspaceListPage)(testCode)
  }

  /**
    * "Signs in" to FireCloud with an access token, bypassing the Google sign-in flow. Assumes the
    * user is not registered and returns a ready TermsOfServicePage.
    */
  def withSignInNewUser(user: Credentials)
                       (testCode: RegistrationPage => Any)(implicit webDriver: WebDriver): Unit = {
      withSignIn(user, new TermsOfServicePage) { tosPage =>
        register cleanUp executeAsyncScript("window.rejectToS(arguments[arguments.length - 1])")

        tosPage.accept()

        val registrationPage = await ready new RegistrationPage

        testCode(registrationPage)

    }
  }


  private def withSignIn[T <: AuthenticatedPage](user: Credentials, page: T, scopes: Seq[String] = AuthTokenScopes.userLoginScopes)
                                                (testCode: T => Any)
                                                (implicit webDriver: WebDriver): Unit = {

    logger.info(s"withSignIn (${user.email}) starting ...")
    executeTestCodeWithSignIn(user, {
      // workaround for failed forceSignedIn
      var counter = 0
      retry(Seq.fill(2)(1.seconds)) ({
        logger.info(s"withSignIn (${user.email}) opening SignInPage ...")
        new SignInPage(FireCloudConfig.FireCloud.baseUrl).open
        logger.info(s"withSignIn (${user.email}) executing script forceSignedIn ...")
        val js = s"window.forceSignedIn('${user.makeAuthToken(scopes).value}')"
        executeScript(js)
        if (counter > 0) logger.warn(s"Retrying execute JavaScript forceSignedIn(): value = $js")
        counter +=1
        try {
          logger.info(s"withSignIn (${user.email}) awaiting page ready ...")
          page.awaitReady()
          logger.info(s"withSignIn (${user.email}) returning ready page.")
          Some(page)
        } catch {
          case t: Throwable =>
            logger.warn(s"withSignIn (${user.email}) errored waiting for page: ${t.getMessage}")
            None
        }
      })
    }, page, testCode)
  }

  private def withSignInReal[T <: AuthenticatedPage](user: Credentials, page: T)
                                                    (testCode: T => Any)
                                                    (implicit webDriver: WebDriver): Unit = {

    logger.info(s"withSignInReal: ${user.email} ...")
    executeTestCodeWithSignIn(user, {
      new SignInPage(FireCloudConfig.FireCloud.baseUrl).open.signIn(user.email, user.password)
    }, page, testCode)
  }

  private def executeTestCodeWithSignIn[T <: AuthenticatedPage](user: Credentials, signIn: => Unit,
                                                                page: T, testCode: T => Any)
                                                               (implicit webDriver: WebDriver): Unit = {

    logger.info(s"executeTestCodeWithSignIn (${user.email}) starting ...")
    signIn
    logger.info(s"executeTestCodeWithSignIn (${user.email}) awaiting page ready ...")
    await ready page
    logger.info(s"Login: ${user.email}. URL: ${page}")
    logger.info(s"executeTestCodeWithSignIn (${user.email}) executing test code ...")

    // Don't try/finally here to prevent sign-out before capturing a failure screenshot
    testCode(page)

    logger.info(s"executeTestCodeWithSignIn (${user.email}) signing out ...")
    try page.signOut() catch nonFatalAndLog(s"ERROR logging out user: ${user.email}")
  }

}
