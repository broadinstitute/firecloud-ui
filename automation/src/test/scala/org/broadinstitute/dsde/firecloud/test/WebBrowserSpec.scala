package org.broadinstitute.dsde.firecloud.test

import org.broadinstitute.dsde.automation.config.Credentials
import org.broadinstitute.dsde.firecloud.api.Orchestration
import org.broadinstitute.dsde.firecloud.config.Config
import org.broadinstitute.dsde.firecloud.page.user.SignInPage
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceListPage
import org.openqa.selenium.WebDriver
import org.scalatest.Suite

/**
  * Base spec for writing FireCloud web browser tests.
  */
trait WebBrowserSpec extends org.broadinstitute.dsde.automation.browser.WebBrowserSpec { self: Suite =>

  val api = Orchestration

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
  def signIn(credentials: Credentials)(implicit webDriver: WebDriver): WorkspaceListPage = {
    signIn(credentials.email, credentials.password)
    new WorkspaceListPage
  }
}
