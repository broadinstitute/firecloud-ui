package org.broadinstitute.dsde.firecloud.test.user

import org.broadinstitute.dsde.firecloud.api.{Rawls, Sam, Thurloe}
import org.broadinstitute.dsde.firecloud.config.{AuthToken, AuthTokens, Config}
import org.broadinstitute.dsde.firecloud.page.library.DataLibraryPage
import org.broadinstitute.dsde.firecloud.page.user.RegistrationPage
import org.broadinstitute.dsde.firecloud.test.{CleanUp, WebBrowserSpec}
import org.scalatest.{BeforeAndAfter, FreeSpec, Matchers}

/**
  * Tests for new user registration scenarios.
  */
class RegistrationSpec extends FreeSpec with BeforeAndAfter with Matchers with WebBrowserSpec with CleanUp {

  val email: String = Config.Users.lunaTemp.email
  val password: String = Config.Users.lunaTemp.password
  val subjectId: String = Config.Users.lunaTempSubjectId

  implicit val authToken: AuthToken = AuthTokens.admin

  // Clean-up anything left over from any previous failures.
  before {
    if (Sam.admin.doesUserExist(subjectId).getOrElse(false)) {
      try { Sam.admin.deleteUser(subjectId) } catch nonFatalAndLog("Error deleting user before test but will try running the test anyway")
    }
    Thurloe.keyValuePairs.deleteAll(subjectId)
  }

  private def registerCleanUpForDeleteUser(subjectId: String): Unit = {
    register cleanUp Sam.admin.deleteUser(subjectId)
    register cleanUp Thurloe.keyValuePairs.deleteAll(subjectId)
  }

  "FireCloud registration" - {

    "should allow a person to register" in withWebDriver { implicit driver =>

      signIn(email, password)
      val registrationPage = new RegistrationPage

      registerCleanUpForDeleteUser(subjectId)

      registrationPage.register(
        firstName = "Test",
        lastName = "Dummy",
        title = "Tester",
        contactEmail = Some("test@firecloud.org"),
        institute = "Broad",
        institutionalProgram = "DSDE",
        nonProfitStatus = true,
        principalInvestigator = "Nobody",
        city = "Cambridge",
        state = "MA",
        country = "USA")
      registrationPage.registerWait()

      new DataLibraryPage().validateLocation()
    }
  }

/*
  behavior of "FireCloud registration page"

  it should "allow sign in of registered user" in withWebDriver { implicit driver =>
    new SignInPage(Config.FireCloud.baseUrl).open.signIn(Config.Users.testUser.email, Config.Users.testUser.password)
  }

  it should "not allow an unregistered user access" in withWebDriver { implicit driver =>
  }

  it should "allow a signed-in user to log out" in withWebDriver { implicit driver =>
  }
*/
}
