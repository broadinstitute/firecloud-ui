package org.broadinstitute.dsde.firecloud.test.igv

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.firecloud.page.workspaces.analysis.WorkspaceAnalysisPage
import org.broadinstitute.dsde.workbench.config.{Credentials, UserPool}
import org.broadinstitute.dsde.workbench.fixture.{BillingFixtures, MethodFixtures, TestReporterFixture, WorkspaceFixtures}
import org.broadinstitute.dsde.workbench.service.test.{CleanUp, WebBrowserSpec}
import org.scalatest.{FreeSpec, Matchers}

class IGVSpec extends FreeSpec with WebBrowserSpec with CleanUp with WorkspaceFixtures with UserFixtures
  with BillingFixtures with MethodFixtures with Matchers with LazyLogging with TestReporterFixture {

  // this is a basic smoke test that validates IGV has rendered on the page. we don't attempt to test IGV functionality;
  // all we verify is that we haven't broken the ability for the third-party IGV JavaScript to render.
  "IGV should render on the page" in {
    val ownerUser: Credentials = UserPool.chooseProjectOwner
    implicit val ownerToken = ownerUser.makeAuthToken

    withCleanBillingProject(ownerUser) { projectName =>
      withWorkspace(projectName, "IGVSpec_basic_render") { workspaceName =>
        withWebDriver { implicit driver =>
          withSignIn(ownerUser) { _ =>
            val igvPage = new WorkspaceAnalysisPage(projectName, workspaceName).open
            await condition igvPage.igvNavbarVisible
          }
        }
      }
    }
  }


}
