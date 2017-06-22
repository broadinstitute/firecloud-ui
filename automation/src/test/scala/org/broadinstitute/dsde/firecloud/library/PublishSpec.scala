package org.broadinstitute.dsde.firecloud.library

import java.util.UUID

import org.broadinstitute.dsde.firecloud.Config
import org.broadinstitute.dsde.firecloud.auth.AuthToken
import org.broadinstitute.dsde.firecloud.data.Library
import org.broadinstitute.dsde.firecloud.pages._
import org.scalatest._

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}


class PublishAsCuratorSpec() extends FreeSpec with WebBrowserSpec with BeforeAndAfterAll with BeforeAndAfterEach {
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  val unpubName: String = "unpub-" + UUID.randomUUID.toString + "-Publish"
  val unpubWAttributesName: String = "unpub-withAttributes" + UUID.randomUUID.toString + "-Publish"
  val namespace: String = Config.Projects.default
  implicit val authToken = AuthToken(Config.Users.curator)

  override def beforeAll(): Unit = {
    // create workspaces
    api.workspaces.create(namespace, unpubName)
    api.workspaces.create(namespace, unpubWAttributesName)
    api.setLibraryAttributes(namespace, unpubWAttributesName, Library.metadata)
  }

  override def beforeEach(): Unit = {
  }

  override def afterEach(): Unit = {
  }

  override def afterAll(): Unit = {
    api.workspaces.delete(namespace, unpubName)
    api.workspaces.delete(namespace, unpubWAttributesName)
  }


  "As a curator" - {
    "on an unpublished workspace" - {
      "without required library attributes" - {
        "publish should open error modal " in withWebDriver { implicit driver =>
          signIn(Config.Users.curator)
          val page = new WorkspaceSummaryPage(namespace, unpubName)
          page.open
          val errormodal = page.ui.clickPublishButton()
          assert(errormodal.validateLocation)
        }
      }
      "with required library attributes" - {
        "should be publishable " in withWebDriver { implicit driver =>
          signIn(Config.Users.curator)
          val page = new WorkspaceSummaryPage(namespace, unpubWAttributesName)
          page.open
          assert(page.ui.hasPublishButton)
        }
      }
    }
  }
}



class PublishAsNonCuratorSpec() extends FreeSpec with WebBrowserSpec with BeforeAndAfterAll with BeforeAndAfterEach {

  implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  val unpubName: String = "unpub-" + UUID.randomUUID.toString + "-Publish"
  val unpubWAttributesName: String = "unpub-withAttributes-" + UUID.randomUUID.toString + "-Publish"
  val namespace: String = Config.Projects.default
  implicit val authToken = AuthToken(Config.Users.harry)

  override def beforeAll(): Unit = {
    // create workspaces
    api.workspaces.create(namespace, unpubName)
    api.updateAcl(namespace, unpubName, Config.Users.ron.email, "WRITER", canshare = false)
    api.workspaces.create(namespace, unpubWAttributesName)
    api.setLibraryAttributes(namespace, unpubWAttributesName, Library.metadata)
    api.updateAcl(namespace, unpubWAttributesName, Config.Users.ron.email, "WRITER", canshare = false)
  }

  override def beforeEach(): Unit = {
  }

  override def afterEach(): Unit = {
  }

  override def afterAll(): Unit = {
    api.workspaces.delete(namespace, unpubName)
    api.workspaces.delete(namespace, unpubWAttributesName)
  }


  "As a non-curator" - {
    "An unpublished workspace" - {
      "with required org.broadinstitute.dsde.firecloud.library attributes" - {
        "should not be publishable " in withWebDriver { implicit driver =>
          signIn(Config.Users.ron)

          val page = new WorkspaceSummaryPage(namespace, unpubName)
          page.open
          assert(!page.ui.hasPublishButton)
        }
      }
    }
  }
}
