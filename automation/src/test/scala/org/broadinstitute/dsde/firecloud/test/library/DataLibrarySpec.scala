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

  "For a dataset with facets" in withWebDriver { implicit driver =>
    val curatorUser = UserPool.chooseCurator
    implicit val authToken: AuthToken = curatorUser.makeAuthToken()
    withWorkspace(namespace, "Facets", attributes = Some(WorkspaceData.tags)) { wsName =>
      withCleanUp {
        //Hard Coded titles in our UI code
        val cohortPhenotypeIndicationSection = "Cohort Phenotype/Indication"
        val experimentalStrategySection = "Experimental Strategy"
        val projectNameSection = "Project Name"
        val primaryDiseaseSiteSection = "Primary Disease Site"
        val dataUseLimitationSection = "Data Use Limitation"

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
          val page = new DataLibraryPage().open
          page.hasDataset(wsName) shouldBe true

          //Verifying results
          val expected = Map(cohortPhenotypeIndicationSection -> s"$wsName+1",
            experimentalStrategySection -> s"$wsName+2",
            projectNameSection -> s"$wsName+3",
            primaryDiseaseSiteSection -> s"$wsName+4",
            dataUseLimitationSection -> s"$wsName+5")

          expected.foreach { case (title, item) =>
            val childElement = cssSelector(s"[data-test-id='$title-facet_section'] [data-test-id='$item-item']").findElement
            println(childElement.getOrElse(throw new Exception(s"child element for $title $item not found")).underlying.getText)
            childElement.getOrElse(throw new Exception("child element not found")).underlying.getText shouldEqual item
          }
        }
      }
    }
  }

}
