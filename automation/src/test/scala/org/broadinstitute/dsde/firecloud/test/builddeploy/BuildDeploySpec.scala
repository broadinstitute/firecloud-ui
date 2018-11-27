package org.broadinstitute.dsde.firecloud.test.builddeploy

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.FireCloudConfig
import org.broadinstitute.dsde.workbench.fixture. TestReporterFixture
import org.broadinstitute.dsde.workbench.service.test.WebBrowserSpec
import org.scalatest.{FreeSpec, Matchers}

class BuildDeploySpec extends FreeSpec with WebBrowserSpec with Matchers with LazyLogging with TestReporterFixture {

  val notFoundTitle = "404 Not Found"

  def testUrl(partialUrl: String) = {
    s"should put $partialUrl in the right place" in {
      withWebDriver { implicit driver =>
        driver.get(s"${FireCloudConfig.FireCloud.baseUrl}$partialUrl")
        withClue(s"GET request to $partialUrl returned 404!!!") {
          driver.getTitle shouldNot be(notFoundTitle)
        }
      }
    }
  }

  "FireCloud UI build and deploy process" - {

    s"should return '$notFoundTitle' in the page title for a missing file" in {
      withWebDriver { implicit driver =>
        driver.get(s"${FireCloudConfig.FireCloud.baseUrl}this-should-never-exist.nope")
        withClue(s"Request to a non-existent url in FireCloud UI should return a page title of '$notFoundTitle'") {
          driver.getTitle should be("404 Not Found")
        }
      }
    }

    testUrl("tcell.js")
    testUrl("newrelic.js")
    testUrl("assets/favicon.ico")
  }

}
