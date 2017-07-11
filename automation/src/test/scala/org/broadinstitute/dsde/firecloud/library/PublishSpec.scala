package org.broadinstitute.dsde.firecloud.library

import java.util.UUID

import org.broadinstitute.dsde.firecloud.{CleanUp, Config}
import org.broadinstitute.dsde.firecloud.auth.AuthToken
import org.broadinstitute.dsde.firecloud.data.Library
import org.broadinstitute.dsde.firecloud.pages._
import org.scalatest._

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}


class PublishSpec() extends FreeSpec with WebBrowserSpec with CleanUp {
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  val namespace: String = Config.Projects.default
  implicit val curatorAuthToken = AuthToken(Config.Users.curator)
  val ronAuthToken = AuthToken(Config.Users.ron)


  "As a curator" - {
    "on an unpublished workspace" - {
      "without required library attributes" - {
        "publish should open error modal " in withWebDriver { implicit driver =>
          val wsName = "PublishSpec_curator_unpub_" + randomUuid
          api.workspaces.create(namespace, wsName)
          register cleanUp api.workspaces.delete(namespace, wsName)

          signIn(Config.Users.curator)
          val page = new WorkspaceSummaryPage(namespace, wsName)
          page.open
          val errormodal = page.ui.clickPublishButton()
          assert(errormodal.validateLocation)
        }
      }
      "with required library attributes" - {
        "should be publishable " in withWebDriver { implicit driver =>
          val wsName = "PublishSpec_curator_unpub_withAttributes_" + randomUuid
          api.workspaces.create(namespace, wsName)
          api.library.setLibraryAttributes(namespace, wsName, Library.metadata)
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
        val wsName = "PublishSpec_curator_pub_" + randomUuid
        api.workspaces.create(namespace, wsName)
        val data = Library.metadata + ("library:datasetName" -> wsName)
        api.library.setLibraryAttributes(namespace, wsName, data)
        api.library.publishWorkspace(namespace, wsName)
        register cleanUp api.library.unpublishWorkspace(namespace, wsName)
        register cleanUp api.workspaces.delete(namespace, wsName)

        signIn(Config.Users.curator)
        val page = new DataLibraryPage()
        page.open
        assert(page.ui.hasDataset(wsName))
      }
  }
  "As a non-curator" - {
    "An unpublished workspace" - {
      "with required org.broadinstitute.dsde.firecloud.library attributes" - {
        "should not be publishable " in withWebDriver { implicit driver =>
          val wsName = "PublishSpec_unpub_withAttributes_" + randomUuid
          api.workspaces.create(namespace, wsName)(ronAuthToken)
          api.library.setLibraryAttributes(namespace, wsName, Library.metadata)(ronAuthToken)
          register cleanUp api.workspaces.delete(namespace, wsName)

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
