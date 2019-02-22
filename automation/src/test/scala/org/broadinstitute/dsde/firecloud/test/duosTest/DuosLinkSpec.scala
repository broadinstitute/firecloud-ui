package org.broadinstitute.dsde.firecloud.test.duosTest

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

  "click on the sign in button" in {
    val user = new Credentials(email = "test.firec@gmail.com", password = "BroadDec1")
    withWebDriver { implicit driver =>
//      val clickOnSignInHomepage = new DuosLoginPage("https://duos.dsde-dev.broadinstitute.org/#/home").open
      withSignInDuos(user) { homePage =>
        println("success")
        val homeSearch: Unit = new DuosHomePage().datasetSearch()
        homeSearch shouldBe("DUOS-000003")
      }
    }
  }

//  "when logged in, type something in the search box and click 'download selection'" in {
//    withWebDriver { implicit driver =>
//      val homeSearch = new DuosHomePage().datasetSearch()
//      homeSearch shouldBe("DUOS-000003")
//    }
//  }
//
//  "verify that the description of the DUOS graphic contains the attribute name" in {
//        withWebDriver { implicit driver =>
//          val duosp = CssSelectorQuery("img[alt*='What is DUOS graphic']")
//          val duosGraphic = new DuosLoginPage("https://duos.dsde-dev.broadinstitute.org/#/home").open.find()
//
//          val duosGraphic = new DuosLoginPage("https://duos.dsde-dev.broadinstitute.org/#/home").open
//            //val duosGraphic = new DuosLoginPage().open.duosP()
//
//            duosGraphic.underlying.isDisplayed shouldBe(true)
//          }
//        }
//
//  "click on the join DUOS button and type username in" in {
//    withWebDriver { implicit driver =>
//
//      val join = new DuosSignInPage().open.joinDuos()
//      // AuthenticatedPage.readUserEmail
//      join shouldBe("test.firec@gmail.com")
//    }
//  }
//
//  "clicking if the help button exists" in {
//        withWebDriver { implicit driver =>
//          val duosHelp = new DuosSignInPage().helpDuos()
//            duosHelp.underlying.isEnabled shouldBe(true)
//          }
//        }
//
//  "testing if the about button exists" in {
//        withWebDriver { implicit driver =>
//          val duosAbout = new DuosSignInPage().open.aboutDuos()
//          duosAbout.underlying.isEnabled shouldBe(true)
//          }
//        }
}