package org.broadinstitute.dsde.firecloud.fixture


import java.io.{File, FileInputStream, FileOutputStream}
import java.text.SimpleDateFormat

import org.broadinstitute.dsde.firecloud.FireCloudConfig
import org.scalatest.concurrent.{Eventually, ScaledTimeSpans}
import org.broadinstitute.dsde.firecloud.page.AuthenticatedPage
import org.broadinstitute.dsde.firecloud.page.user.{RegistrationPage, SignInPage, TermsOfServicePage}
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceListPage
import org.broadinstitute.dsde.workbench.auth.AuthTokenScopes
import org.broadinstitute.dsde.workbench.config.Credentials
import org.broadinstitute.dsde.workbench.service.test.{CleanUp, WebBrowserSpec}
import org.openqa.selenium.{OutputType, TakesScreenshot, WebDriver}
import org.scalatest.TestSuite
import org.broadinstitute.dsde.workbench.service.util.Retry.retry
import org.openqa.selenium.remote.Augmenter

import scala.concurrent.duration._

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
    withSignIn(user, {
      // workaround for failed forceSignedIn
      var counter = 0
      retry(Seq.fill(2)(1.seconds)) ({

        logger.info(s"Open page URL ${FireCloudConfig.FireCloud.baseUrl}")
        executeScript(s"window.location.href='${FireCloudConfig.FireCloud.baseUrl}'")

        logger.info("sleeping 1 second")
        Thread.sleep(1000)

        val date = new SimpleDateFormat("HH-mm-ss-SSS").format(new java.util.Date())
        val path = "failure_screenshots"
        val name = s"${suiteName}_${date}"
        val screenshotFileName = s"$path/${name}.png"
        val tmpFile = new Augmenter().augment(webDriver).asInstanceOf[TakesScreenshot].getScreenshotAs(OutputType.FILE)
        logger.info(s"Login page screenshot saved to $screenshotFileName")
        new FileOutputStream(new File(screenshotFileName)).getChannel.transferFrom(
          new FileInputStream(tmpFile).getChannel, 0, Long.MaxValue)

        logger.info("SignIn page wait for ready")
        await ready new SignInPage(FireCloudConfig.FireCloud.baseUrl)

        logger.info("SignIn page execute Javascript forceSignedIn")
        executeScript(s"window.forceSignedIn('${user.makeAuthToken(scopes).value}')")

        if (counter > 0) logger.warn(s"Retrying forceSignedIn. $counter")
        counter +=1
        try {
          logger.info(("try block: page awaitReady."))
          page.awaitReady()
          Some(page)
        } catch {
          case _: Throwable =>
            None
        }
      })

    }, page, testCode)
  }

  private def withSignInReal[T <: AuthenticatedPage](user: Credentials, page: T)
                                                    (testCode: T => Any)
                                                    (implicit webDriver: WebDriver): Unit = {
    withSignIn(user, {
      new SignInPage(FireCloudConfig.FireCloud.baseUrl).open
        .signIn(user.email, user.password)
    }, page, testCode)
  }

  private def withSignIn[T <: AuthenticatedPage](user: Credentials, signIn: => Unit,
                                                         page: T, testCode: T => Any)
                                                        (implicit webDriver: WebDriver): Unit = {
    signIn
    await ready page
    logger.info(s"Login: ${user.email}. URL: ${page}")

    // Don't try/finally here to prevent sign-out before capturing a failure screenshot
    testCode(page)

    try page.signOut() catch nonFatalAndLog(s"ERROR logging out user: ${user.email}")
  }

}
