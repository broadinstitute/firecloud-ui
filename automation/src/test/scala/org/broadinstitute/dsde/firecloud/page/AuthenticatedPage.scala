package org.broadinstitute.dsde.firecloud.page

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

  def filter(searchText: String): Unit = {
    ui.filter(searchText)
  }

  def getUser(): String = {
    ui.getUser()
  }


  trait UI {
    private val accountDropdown = testId("account-dropdown")
    private val signOutLink = testId("sign-out")
    private val filterInput = testId("-input")
    private val emailRegEx = """(?i)\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,4}\b""".r

    def checkAccountDropdown: Boolean = {
      find(accountDropdown).isDefined
    }

    def clickAccountDropdown(): Unit = {
      click on accountDropdown
    }

    def clickSignOut(): Unit = {
      click on (await enabled signOutLink)
    }

    def filter(searchText: String) = {
      await enabled filterInput
      searchField(filterInput).value = searchText
      pressKeys("\n")
    }

    def getUser() = {
      await enabled accountDropdown
      val userDropdownText = find(accountDropdown).get.text
      emailRegEx findFirstIn userDropdownText match {
        case Some(email) => email
        case None => ""
      }
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
