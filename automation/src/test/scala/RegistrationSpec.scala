import org.broadinstitute.dsde.firecloud.Config
import org.broadinstitute.dsde.firecloud.pages.{DataLibraryPage, RegistrationPage, SignInPage, WebBrowserSpec}
import org.scalatest.FlatSpec

/**
  * Tests for new user registration scenarios.
  */
class RegistrationSpec extends FlatSpec with WebBrowserSpec {

  behavior of "FireCloud registration page"

  it should "allow sign in of registered user" in withWebDriver { implicit driver =>
    new SignInPage(Config.FireCloud.baseUrl).open.signIn(Config.Users.testUser.email, Config.Users.testUser.password)
  }

  it should "allow a user to register" in withWebDriver { implicit driver =>
    new SignInPage(Config.FireCloud.baseUrl).open.signIn(Config.Users.lunaTemp.email, Config.Users.lunaTemp.password)

    new RegistrationPage().register(firstName = "Test", lastName = "Dummy", title = "Tester",
      contactEmail = Config.Users.lunaTemp.email, institute = "Broad", institutionalProgram = "DSDE",
      nonProfitStatus = true, principalInvestigator = "Nobody", city = "Cambridge",
      state = "MA", country = "USA")

    new DataLibraryPage().validateLocation()
  }

  it should "not allow an unregistered user access" in withWebDriver { implicit driver =>


  }

  it should "allow a signed-in user to log out" in withWebDriver { implicit driver =>

  }





}
