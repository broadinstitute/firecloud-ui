package org.broadinstitute.dsde.firecloud.test.builddeploy

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.FireCloudConfig
import org.broadinstitute.dsde.workbench.fixture.TestReporterFixture
import org.broadinstitute.dsde.workbench.service.test.WebBrowserSpec
import org.openqa.selenium.By
import org.scalatest.{FreeSpec, Matchers}

class BuildDeploySpec extends FreeSpec with WebBrowserSpec with Matchers with LazyLogging with TestReporterFixture {

  // 404s return the default Apache page, which has a title of "404 Not Found"
  val notFoundTitle = "404 Not Found"

  "FireCloud UI build and deploy process" - {

    // this test ensures that 404s always return a title of "404 Not Found". If they stop doing so, the other
    // tests in this spec are invalid and need updating.
    s"should return '$notFoundTitle' in the page title for a missing file" in {
      withWebDriver { implicit driver =>
        driver.get(s"${FireCloudConfig.FireCloud.baseUrl}this-should-never-exist.nope")
        withClue(s"Request to a non-existent url in FireCloud UI should return a page title of '$notFoundTitle'") {
          driver.getTitle should be("404 Not Found")
        }
      }
    }

    // these tests ensure that the build/deploy process has generated files at the correct urls
    List("tcell.js","assets/favicon.ico").foreach { partialUrl =>

      s"should put $partialUrl in the right place" in {
        withWebDriver { implicit driver =>
          driver.get(s"${FireCloudConfig.FireCloud.baseUrl}$partialUrl")
          withClue(s"GET request to $partialUrl returned 404!!!") {
            driver.getTitle shouldNot be(notFoundTitle)
          }
        }
      }

    }

    // this test ensures the build outputs a git commit hash value. As a side effect, this test also
    // logs the value it finds, so we have a record - albeit buried - of the exact codebase on which
    // the test ran.
    "should populate a commithash value in HTML source" in {
      withWebDriver { implicit driver =>
        driver.get(s"${FireCloudConfig.FireCloud.baseUrl}index.html")
        val commithash = driver
          .findElement(By.xpath("//meta[@name='application-name']"))
          .getAttribute("data-commithash")

        logger.info(s"unit test commithash: $commithash")

        commithash shouldNot be(empty)

      }
    }

  }

}
