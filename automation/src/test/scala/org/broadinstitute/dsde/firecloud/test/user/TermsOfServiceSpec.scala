package org.broadinstitute.dsde.firecloud.test.user

import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.firecloud.page.user.TermsOfServicePage
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceListPage
import org.broadinstitute.dsde.workbench.config.UserPool
import org.broadinstitute.dsde.workbench.fixture.TestReporterFixture
import org.broadinstitute.dsde.workbench.service.test.WebBrowserSpec
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FreeSpec, Matchers}

class TermsOfServiceSpec extends FreeSpec with WebBrowserSpec with UserFixtures with Matchers with Eventually with TestReporterFixture {

  implicit override val patienceConfig = PatienceConfig(timeout = scaled(Span(10, Seconds)), interval = scaled(Span(500, Millis)))

  "A user who has not previously accepted the Terms of Service" - {
    "should be required to accept them before using the application" in withWebDriver { implicit driver =>
      val user = UserPool.chooseStudent

      withSignInNeedsTerms(user) { tosPage =>

        tosPage.accept()

        await ready new WorkspaceListPage()
      }
    }
  }
}
