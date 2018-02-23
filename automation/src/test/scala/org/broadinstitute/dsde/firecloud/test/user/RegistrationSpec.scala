package org.broadinstitute.dsde.firecloud.test.user

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.firecloud.page.library.DataLibraryPage
import org.broadinstitute.dsde.firecloud.page.user.ProfilePage
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceListPage
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{Config, Credentials, UserPool}
import org.broadinstitute.dsde.workbench.service.{Sam, Thurloe}
import org.broadinstitute.dsde.workbench.service.test.{CleanUp, WebBrowserSpec}
import org.scalatest.{BeforeAndAfter, FreeSpec, Matchers}


/**
  * Tests for new user registration scenarios.
  */
class RegistrationSpec extends FreeSpec with BeforeAndAfter with Matchers with WebBrowserSpec
  with UserFixtures with CleanUp with LazyLogging {

  val testUser: Credentials = Config.Users.temp  // TODO: pull from user pool and fetch correct subject ID
  val subjectId: String = Config.Users.tempSubjectId

  val adminUser: Credentials = UserPool.chooseAdmin
  implicit val authToken: AuthToken = adminUser.makeAuthToken()

  // Clean-up anything left over from any previous failures.
  before {
    logger.debug(adminUser.email)
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

      withSignInNewUserReal(testUser) { registrationPage =>
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

        new DataLibraryPage().validateLocation()

        val profilePage = new ProfilePage().open
        val username = testUser.email.split("@").head
/* Re-enable this code and remove the temporary code below after fixing rawls for GAWB-2933
        profilePage.readProxyGroupEmail should (startWith (username) and endWith ("firecloud.org"))
*/
        profilePage.readProxyGroupEmail should (startWith ("PROXY_") and endWith ("firecloud.org"))
/**/
      }
    }
  }

  "should show billing account instructions for a newly registered user" in withWebDriver { implicit driver =>

    withSignInNewUserReal(testUser) { registrationPage =>
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

      await ready new DataLibraryPage()

      val listPage = new WorkspaceListPage().open
      listPage.clickCreateWorkspaceButton(true)

      listPage.showsNoBillingProjectsModal() shouldBe true
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
