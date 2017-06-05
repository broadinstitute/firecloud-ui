package org.broadinstitute.dsde.firecloud.pages

import org.openqa.selenium.WebDriver

/**
  * Base class for pages that are reachable after signing in.
  */
abstract class AuthenticatedPage(implicit webDriver: WebDriver) extends FireCloudView {

  /**
    * Sign out of FireCloud.
    */
  def signOut(): Unit = {
    ui.clickAccountDropdown()
    ui.clickSignOut()
  }


  trait UI {
    private val accountDropdown = testId("account-dropdown")
    private val signOutLink = testId("sign-out")

    def checkAccountDropdown: Boolean = {
      accountDropdown.findElement.isDefined
    }

    def clickAccountDropdown(): Unit = {
      click on accountDropdown
    }

    def clickSignOut(): Unit = {
      click on (await enabled signOutLink)
    }
  }
  // TODO: figure out how to make this not have to be private? might be okay as long as we never extend a non-abstract page class...
  private object ui extends UI
}
