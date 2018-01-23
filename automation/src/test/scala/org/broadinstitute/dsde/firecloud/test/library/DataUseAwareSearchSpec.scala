package org.broadinstitute.dsde.firecloud.test.library

import org.broadinstitute.dsde.firecloud.config.UserPool
import org.broadinstitute.dsde.firecloud.fixture.{UserFixtures, WorkspaceFixtures}
import org.broadinstitute.dsde.firecloud.page.library.DataLibraryPage
import org.broadinstitute.dsde.firecloud.test.{CleanUp, WebBrowserSpec}
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class DataUseAwareSearchSpec extends FreeSpec with WebBrowserSpec with UserFixtures with WorkspaceFixtures with CleanUp with Matchers {
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  "Open the page" - {
    "Open the RP modal" in withWebDriver { implicit driver =>
      val user = UserPool.chooseAnyUser

      withSignIn(user) { _ =>
        val page = new DataLibraryPage().open

        page.openResearchPurposeModal()

        page.isShowingResearchPurposeModal shouldBe true
      }
    }
  }

}
