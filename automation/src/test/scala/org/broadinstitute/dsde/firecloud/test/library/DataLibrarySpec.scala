package org.broadinstitute.dsde.firecloud.test.library

import org.broadinstitute.dsde.firecloud.component.Label
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


  "Dataset to test facets values" ignore  withWebDriver { implicit driver =>
    val curatorUser = UserPool.chooseCurator
    implicit val authToken: AuthToken = curatorUser.makeAuthToken()
    withWorkspace(namespace, "Facets", attributes = Some(WorkspaceData.tags)) { wsName =>
      withCleanUp {

        //replacing values in the basic library dataset
        val data = LibraryData.metadataBasic + ("library:datasetName" -> wsName,
          "library:indication" -> s"$wsName+1",
          "library:datatype" -> Seq(s"$wsName+2"),
          "library:projectName" -> s"$wsName+3",
          "library:primaryDiseaseSite" -> s"$wsName+4",
          "library:dataUseRestriction" -> s"$wsName+5")

        api.library.setLibraryAttributes(namespace, wsName, data)
        register cleanUp api.library.unpublishWorkspace(namespace, wsName)
        api.library.publishWorkspace(namespace, wsName)

        withSignIn(curatorUser) { _ =>
          val pageOption = new DataLibraryPage().waitForDataset(wsName)

          pageOption should not be None

          val page = pageOption.get

          //val page = new DataLibraryPage().open
          page.hasDataset(wsName) shouldBe true



          //Verifying results
          val expected = Map(page.cohortPhenotypeIndicationSection -> s"$wsName+1",
            page.experimentalStrategySection -> s"$wsName+2",
            page.projectNameSection -> s"$wsName+3",
            page.primaryDiseaseSiteSection -> s"$wsName+4",
            page.dataUseLimitationSection -> s"$wsName+5")

          expected.foreach { case (title, item) =>
            val childElement = cssSelector(s"[data-test-id='$title-facet-section'] [data-test-id='$item-item']").findElement
            childElement should not be None
            childElement.get.underlying.getText shouldBe item
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

    withWorkspace(namespace, "DataLibrarySpec") { wsName =>
      withWorkspace(namespace, "DataLibrarySpec_notags") { wsNameNoTag =>
        withCleanUp {

          val dataset = LibraryData.metadataBasic + ("library:datasetName" -> wsName)
          val attrTag = Map("tag:tags" -> Seq(s"aaaTag$wsName", s"cccTag$wsName"))
          api.library.setLibraryAttributes(namespace, wsName, dataset)
          api.library.setLibraryAttributes(namespace, wsNameNoTag, dataset)
          api.workspaces.setAttributes(namespace, wsName, attrTag)
          register cleanUp {
            api.library.unpublishWorkspace(namespace, wsName)(authToken)
            api.library.unpublishWorkspace(namespace, wsNameNoTag)(authToken)
          }
          api.library.publishWorkspace(namespace, wsNameNoTag)
          api.library.publishWorkspace(namespace, wsName)

          withSignIn(curatorUser) { _ =>
            val page = new DataLibraryPage().open

            // entering multiple tags in Tags input-field
            val expectedTags = attrTag.get("tag:tags").get
            page.doTagSearch(expectedTags)
            val rows: List[Map[String, String]] = page.getRows
            val codes: Seq[String] = page.getTags

            page.hasDataset(wsName) shouldBe true
            page.hasDataset(wsNameNoTag) shouldBe false
            codes should contain allElementsOf expectedTags
          }
        }
      }
    }
  }
}

