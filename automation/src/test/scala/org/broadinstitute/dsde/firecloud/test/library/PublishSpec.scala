package org.broadinstitute.dsde.firecloud.test.library

import org.broadinstitute.dsde.firecloud.page._
import org.broadinstitute.dsde.firecloud.config.{AuthToken, Config}
import org.broadinstitute.dsde.firecloud.fixture.LibraryData
import org.broadinstitute.dsde.firecloud.page.library.DataLibraryPage
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceSummaryPage
import org.broadinstitute.dsde.firecloud.test.{CleanUp, WebBrowserSpec}
import org.scalatest._

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}


class PublishSpec extends FreeSpec with WebBrowserSpec with CleanUp {
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  val namespace: String = Config.Projects.default
  implicit val curatorAuthToken = AuthToken(Config.Users.curator)
  val ronAuthToken = AuthToken(Config.Users.ron)


  "For a user with publish permissions" - {
    "an unpublished workspace" - {
      "without required library attributes" - {
        "publish button should be visible but should open error modal when clicked" in withWebDriver { implicit driver =>
          val wsName = "PublishSpec_curator_unpub_" + randomUuid
          api.workspaces.create(namespace, wsName)
          register cleanUp api.workspaces.delete(namespace, wsName)

          signIn(Config.Users.curator)
          val page = new WorkspaceSummaryPage(namespace, wsName)
          page.open
          page.ui.clickPublishButton()
          val errorModal = ErrorModal()
          assert(errorModal.validateLocation)
        }
      }
      "with required library attributes" - {
        "publish button should be visible " in withWebDriver { implicit driver =>
          val wsName = "PublishSpec_curator_unpub_withAttributes_" + randomUuid
          api.workspaces.create(namespace, wsName)
          api.library.setLibraryAttributes(namespace, wsName, LibraryData.metadata)
          register cleanUp api.workspaces.delete(namespace, wsName)

          signIn(Config.Users.curator)
          val page = new WorkspaceSummaryPage(namespace, wsName)
          page.open
          assert(page.ui.hasPublishButton)
        }
      }
    }
    "a published workspace" - {
      "should be visible in the library table" in withWebDriver { implicit driver =>
        val wsName = "PublishSpec_curator_publish_" + randomUuid

        register cleanUp api.workspaces.delete(namespace, wsName)
        api.workspaces.create(namespace, wsName)

        val data = LibraryData.metadata + ("library:datasetName" -> wsName)
        api.library.setLibraryAttributes(namespace, wsName, data)
        register cleanUp api.library.unpublishWorkspace(namespace, wsName)
        api.library.publishWorkspace(namespace, wsName)

        signIn(Config.Users.curator)
        val page = new DataLibraryPage().open
        assert(page.ui.hasDataset(wsName))
      }
      "should be able to be unpublished" in withWebDriver { implicit driver =>
        val wsName = "PublishSpec_curator_unpublish_" + randomUuid

        register cleanUp api.workspaces.delete(namespace, wsName)
        api.workspaces.create(namespace, wsName)

        val data = LibraryData.metadata + ("library:datasetName" -> wsName)
        api.library.setLibraryAttributes(namespace, wsName, data)
        register cleanUp api.library.unpublishWorkspace(namespace, wsName)
        api.library.publishWorkspace(namespace, wsName)

        signIn(Config.Users.curator)
        val wspage = new WorkspaceSummaryPage(namespace, wsName)
        wspage.open
        wspage.unpublishWorkspace()
        val libraryPage = new DataLibraryPage()
        libraryPage.open
        assert(!libraryPage.ui.hasDataset(wsName))
      }

    }

    "As a non-curator" - {
      "an unpublished workspace" - {
        "with required library attributes" - {
          "should not see publish button " in withWebDriver { implicit driver =>
            val wsName = "PublishSpec_unpub_withAttributes_" + randomUuid

            register cleanUp api.workspaces.delete(namespace, wsName)(ronAuthToken)
            api.workspaces.create(namespace, wsName)(ronAuthToken)
            api.library.setLibraryAttributes(namespace, wsName, LibraryData.metadata)(ronAuthToken)

            signIn(Config.Users.ron)
            val page = new WorkspaceSummaryPage(namespace, wsName)
            page.open
            assert(!page.ui.hasPublishButton)
          }
        }
      }
    }
  }
}
