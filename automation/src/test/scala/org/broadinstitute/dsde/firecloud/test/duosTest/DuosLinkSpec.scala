package org.broadinstitute.dsde.firecloud.test.duosTest

import java.util.concurrent.TimeUnit

import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.firecloud.page.duos.{DuosHomePage, DuosLoginPage}
import org.broadinstitute.dsde.firecloud.page.{AuthenticatedPage, BaseFireCloudPage, PageUtil}
import org.broadinstitute.dsde.workbench.fixture.{BillingFixtures, TestReporterFixture, WorkspaceFixtures}
import org.broadinstitute.dsde.workbench.service.test.WebBrowserSpec
import org.openqa.selenium.{By, WebDriver}
import org.openqa.selenium.chrome.ChromeDriver
import org.scalatest.{FreeSpec, Matchers, ParallelTestExecution}
import org.broadinstitute.dsde.workbench.config.UserPool
import org.broadinstitute.dsde.workbench.config.{CommonConfig, Credentials}


/**
  * Tests for the DUOS ui.
  */

class DuosLinkSpec extends FreeSpec with ParallelTestExecution with Matchers
  with WebBrowserSpec with WorkspaceFixtures with UserFixtures with BillingFixtures with TestReporterFixture {

  "sign in and apply for access" in {
    val user = new Credentials(email = "test.firec@gmail.com", password = "BroadDec1")
    withWebDriver { implicit driver =>
      goTo("https://duos.dsde-dev.broadinstitute.org/#/home")
      withSignInDuos(user) { homePage =>
        homePage.datasetSearch()
        val datarequest = cssSelector("div[class='main-title-description']")
        find(datarequest).get.text should include("Data Access Committee")
        println("success")
      }
    }
  }

    "clicking help and about button" in {
          withWebDriver { implicit driver =>
            goTo("https://duos.dsde-dev.broadinstitute.org/#/home")

            val duosp = find(CssSelectorQuery("img[alt*='What is DUOS graphic']"))
            duosp.get.isDisplayed shouldBe(true)

            val helpButton = find(CssSelectorQuery("a[href='#/home_help']"))
            helpButton.get.isEnabled shouldBe (true)

            val aboutButton = find(CssSelectorQuery("a[href='#/home_about']"))
            aboutButton.get.isEnabled shouldBe(true)

            val email = "test.firec@gmail.com"
            val joinButton = CssSelectorQuery("a.navbar-duos-link-join")

            find(joinButton).get.underlying.click()
            Thread.sleep(1000)
            val typeInDes = CssSelectorQuery("input[ng-model='form.name']")
            find(typeInDes).get.asInstanceOf[ValueElement].value_=(email)

            val fullnametext = cssSelector("label[class='home-control-label col-lg-12 col-md-12 col-sm-12 col-xs-12']")
            find(fullnametext).get.text shouldBe("Full Name")
            }
          }
}