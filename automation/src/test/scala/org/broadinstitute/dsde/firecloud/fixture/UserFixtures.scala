package org.broadinstitute.dsde.firecloud.fixture

import org.broadinstitute.dsde.firecloud.config.{AuthToken, Config, Credentials}
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceListPage
import org.broadinstitute.dsde.firecloud.test.{CleanUp, WebBrowserSpec}
import org.openqa.selenium.WebDriver
import org.scalatest.Suite

trait UserFixtures extends CleanUp { self: WebBrowserSpec with Suite =>

  def withSignIn(user: Credentials)
                (testCode: (WorkspaceListPage) => Any)(implicit webdriver: WebDriver): Unit = {

    val listPage = signIn(user)
    val userEmail: String = user.email

    testCode(listPage)
    try {
      listPage.signOut()
    } catch nonFatalAndLog(s"Error logging out user: $userEmail")

  }

}
