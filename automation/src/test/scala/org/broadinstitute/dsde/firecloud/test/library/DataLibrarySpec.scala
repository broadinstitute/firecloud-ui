package org.broadinstitute.dsde.firecloud.test.library

import org.broadinstitute.dsde.firecloud.auth.AuthToken
import org.broadinstitute.dsde.firecloud.config.{Config, UserPool}
import org.broadinstitute.dsde.firecloud.fixture.{LibraryData, UserFixtures, WorkspaceFixtures}
import org.broadinstitute.dsde.firecloud.page.library.DataLibraryPage
import org.broadinstitute.dsde.firecloud.page.workspaces.summary.WorkspaceSummaryPage
import org.broadinstitute.dsde.firecloud.test.{CleanUp, WebBrowserSpec}
import org.scalatest.{FreeSpec, Matchers}

class DataLibrarySpec extends FreeSpec with WebBrowserSpec with UserFixtures with WorkspaceFixtures with CleanUp with Matchers {

  val namespace: String = Config.Projects.default

  "For a dataset with tags" in withWebDriver { implicit driver =>
    val curatorUser = UserPool.chooseCurator
    print(curatorUser.email)
    implicit val authToken: AuthToken = curatorUser.makeAuthToken()
    withWorkspace(namespace, "DataLibrarySpec_tags_", cleanUp = true) { wsName =>
      withCleanUp {
        val data = LibraryData.metadata + ("library:datasetName" -> wsName) ++ LibraryData.consentCodes
        api.library.setLibraryAttributes(namespace, wsName, data)
        register cleanUp api.library.unpublishWorkspace(namespace, wsName)
        api.library.publishWorkspace(namespace, wsName)
        withSignIn(curatorUser) { _ =>
          val page = new DataLibraryPage().waitForDataset(wsName)
          if (page.isDefined) {
            val codes: Seq[String] = page.get.getConsentCodes(wsName)
            List("HMB", "NCU", "NMDS", "NPU") should contain (theSameElementsAs(codes))
          }
        }
      }
    }
  }
}
