package org.broadinstitute.dsde.firecloud.test.library

import org.broadinstitute.dsde.firecloud.fixture.{LibraryData, UserFixtures, WorkspaceData}
import org.broadinstitute.dsde.firecloud.page.library.DataLibraryPage
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{Config, UserPool}
import org.broadinstitute.dsde.workbench.fixture.WorkspaceFixtures
import org.broadinstitute.dsde.workbench.service.test.{CleanUp, WebBrowserSpec}
import org.scalatest.{FreeSpec, Matchers}

class DataLibrarySpec extends FreeSpec with WebBrowserSpec with UserFixtures with WorkspaceFixtures with CleanUp with Matchers {

  val namespace: String = Config.Projects.default

  "For a dataset with consent codes" in withWebDriver { implicit driver =>
    val curatorUser = UserPool.chooseCurator
    implicit val authToken: AuthToken = curatorUser.makeAuthToken()
    withWorkspace(namespace, "DataLibrarySpec_consentcodes") { wsName =>
      withCleanUp {
        val data = LibraryData.metadataBasic + ("library:datasetName" -> wsName) ++ LibraryData.consentCodes
        api.library.setLibraryAttributes(namespace, wsName, data)
        register cleanUp api.library.unpublishWorkspace(namespace, wsName)
        api.library.publishWorkspace(namespace, wsName)
        withSignIn(curatorUser) { _ =>
          val page = new DataLibraryPage().waitForDataset(wsName)
          if (page.isDefined) {
            val codes: Seq[String] = page.get.getConsentCodes
            Seq("HMB", "NCU", "NMDS", "NPU") should contain allElementsOf codes
          }
        }
      }
    }
  }

  "For a dataset with tags" in withWebDriver { implicit driver =>
    val curatorUser = UserPool.chooseCurator
    implicit val authToken: AuthToken = curatorUser.makeAuthToken()
    withWorkspace(namespace, "DataLibrarySpec_tags", attributes = Some(WorkspaceData.tags)) { wsName =>
      withCleanUp {
        val data = LibraryData.metadataBasic + ("library:datasetName" -> wsName)
        api.library.setLibraryAttributes(namespace, wsName, data)
        register cleanUp api.library.unpublishWorkspace(namespace, wsName)
        api.library.publishWorkspace(namespace, wsName)
        withSignIn(curatorUser) { _ =>
          val page = new DataLibraryPage().waitForDataset(wsName)
          if (page.isDefined) {
            val codes: Seq[String] = page.get.getTags
            Seq("testing", "diabetes") should contain allElementsOf codes
          }
        }
      }
    }
  }


  /**
    * Test creates two workspaces. Only one workspace has tags
    */
  "Input multiple tags in filter field" in withWebDriver { implicit driver =>
    val curatorUser = UserPool.chooseCurator
    implicit val authToken: AuthToken = curatorUser.makeAuthToken()
    val tagAC = Map("tag:tags" -> Seq("aaaTag", "cccTag"))
    withWorkspace(namespace, "DataLibrarySpec", attributes = Some(tagAC)) { wsName =>
      withWorkspace(namespace, "DataLibrarySpec_notags") { wsNameNoTag =>
        withCleanUp {

          val dataset = LibraryData.metadataBasic + ("library:datasetName" -> wsName)
          api.library.setLibraryAttributes(namespace, wsName, dataset)
          api.library.setLibraryAttributes(namespace, wsNameNoTag, dataset)
          register cleanUp {
            api.library.unpublishWorkspace(namespace, wsName)(authToken)
            api.library.unpublishWorkspace(namespace, wsNameNoTag)(authToken)
          }
          api.library.publishWorkspace(namespace, wsNameNoTag)
          api.library.publishWorkspace(namespace, wsName)

          withSignIn(curatorUser) { _ =>
            val page = new DataLibraryPage().open

            // entering multiple tags in search field
            val tags = tagAC.get("tag:tags").get
            val rows: List[Map[String, String]] = page.doTagsSearch(tags: _*)
            val codes: Seq[String] = page.getTags

            tags should contain allElementsOf codes // tags all matching
            rows.foreach { row => row("Cohort Name") shouldEqual (wsName) } // verify wsNameNoTag not in table
          }
        }
      }
    }
  }


}
