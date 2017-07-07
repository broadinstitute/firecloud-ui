import org.broadinstitute.dsde.firecloud.api.{Rawls, Thurloe}
import org.broadinstitute.dsde.firecloud.auth.{AuthToken, AuthTokens}
import org.broadinstitute.dsde.firecloud.{CleanUp, Config}
import org.broadinstitute.dsde.firecloud.pages.{DataLibraryPage, RegistrationPage, WebBrowserSpec}
import org.scalatest.{FreeSpec, Matchers}

/**
  * Tests for new user registration scenarios.
  */
class RegistrationSpec extends FreeSpec with Matchers with WebBrowserSpec with CleanUp {

  val email = Config.Users.lunaTemp.email
  val password = Config.Users.lunaTemp.password
  val subjectId = "117891551413045861932"

  implicit val authToken: AuthToken = AuthTokens.admin

  private def registerCleanUpForDeleteUser(subjectId: String): Unit = {
    register cleanUp Rawls.admin.deleteUser(subjectId)
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
        contactEmail = "test@firecloud.org",
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
