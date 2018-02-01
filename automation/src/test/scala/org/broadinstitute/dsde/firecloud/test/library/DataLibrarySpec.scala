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
    withWorkspace(namespace, "DataLibrarySpec_consentcodes_") { wsName =>
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
    withWorkspace(namespace, "DataLibrarySpec_tags_", attributes = Some(WorkspaceData.tags)) { wsName =>
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
    * This test creates two workspace associated with different tags. Search for tag should
    *  only find right workspace in Data Library table.
    */
  "Use a known tag to search for dataset" in withWebDriver { implicit driver =>
    val curatorUser = UserPool.chooseCurator
    implicit val authToken: AuthToken = curatorUser.makeAuthToken()
    withWorkspace(namespace, "DataLibrarySpec_tags_", attributes = Some(WorkspaceData.tagA)) { wsName1 =>
      withWorkspace(namespace, "DataLibrarySpec_tags_", attributes = Some(WorkspaceData.tagB)) { wsName2 =>
        withCleanUp {

          val tag1: String = WorkspaceData.tagA.get("tag:tags").get(0)
          val tag2: String = WorkspaceData.tagB.get("tag:tags").get(0)
          val data1 = LibraryData.metadata + ("library:datasetName" -> wsName1)
          api.library.setLibraryAttributes(namespace, wsName1, data1)
          val data2 = LibraryData.metadata + ("library:datasetName" -> wsName2)
          api.library.setLibraryAttributes(namespace, wsName2, data2)
          register cleanUp {
            api.library.unpublishWorkspace(namespace, wsName1)(authToken)
            api.library.unpublishWorkspace(namespace, wsName2)(authToken)
          }
          api.library.publishWorkspace(namespace, wsName1)
          api.library.publishWorkspace(namespace, wsName2)

          withSignIn(curatorUser) { _ =>
            val page = new DataLibraryPage().open

            // search by tag1 alone
            val aRows: List[Map[String, String]] = page.doTagsSearch(tag1)
            // check: wsName1 and tag1 should be found in table
            aRows.exists { row => row("Cohort Name") == wsName1 && row("Tags").contains(tag1) } shouldBe true
            // check: wsName2 and tag2 should not be found in table
            aRows.exists { row => row("Cohort Name") == wsName2 || row("Tags").contains(tag2) } shouldBe false

            // search by tag2 alone
            val bRows: List[Map[String, String]] = page.doTagsSearch(tag2)
            // check: wsName2 and tag2 should be found in table
            bRows.exists { row => row("Cohort Name") == wsName2 && row("Tags").contains(tag2) } shouldBe true
            // check: wsName1 and tag1 should not be found in table
            bRows.exists { row => row("Cohort Name") == wsName1 || row("Tags").contains(tag1) } shouldBe false

            // clear Tags field to find all workspaces
            page.clearTag()
            aRows.exists { row => row("Cohort Name") == wsName1 && row("Tags").contains(tag1) } shouldBe true
            bRows.exists { row => row("Cohort Name") == wsName2 && row("Tags").contains(tag2) } shouldBe true
          }

        }
      }
    }
  }


}
