package org.broadinstitute.dsde.firecloud.library

import java.util.UUID

import org.broadinstitute.dsde.firecloud.Config
import org.broadinstitute.dsde.firecloud.api._
import org.broadinstitute.dsde.firecloud.data.Library
import org.broadinstitute.dsde.firecloud.pages._
import org.scalatest._

import scala.concurrent.ExecutionContext


class PublishAsCuratorSpec() extends FreeSpec with WebBrowserSpec with BeforeAndAfterAll with BeforeAndAfterEach {
  implicit val ec = ExecutionContext.global

  val unpubName = "unpub-" + UUID.randomUUID.toString + "-Publish"
  val unpubWAttributesName = "unpub-withAttributes" + UUID.randomUUID.toString + "-Publish"
  val namespace = "broad-dsde-dev"

  override def beforeAll(): Unit = {
    // create workspaces
    implicit val authToken = Config.AuthTokens.testFireC
    Orchestration.workspaces.create(namespace, unpubName)
    Orchestration.workspaces.create(namespace, unpubWAttributesName)
    Orchestration.setLibraryAttributes(namespace, unpubWAttributesName, Library.metadata)
  }

  override def beforeEach(): Unit = {
  }

  override def afterEach(): Unit = {
  }

  override def afterAll(): Unit = {
    implicit val authToken = Config.AuthTokens.testFireC
    Orchestration.workspaces.delete("broad-dsde-dev", unpubName)
    Orchestration.workspaces.delete("broad-dsde-dev", unpubWAttributesName)
  }


  "As a curator" - {
    "on an unpublished workspace" - {
      "without required library attributes" - {
        "publish should open error modal " in withWebDriver { implicit driver =>
          signIn(Config.Accounts.curatorUserEmail, Config.Accounts.curatorUserPassword)
          val page = new WorkspaceSummaryPage(namespace, unpubName)
          page.open
          val errormodal = page.ui.clickPublishButton()
          assert(errormodal.validateLocation)
        }
      }
      "with required library attributes" - {
        //withLibraryMetadata(wsname) { wsname2 =>
        "should be publishable " in withWebDriver { implicit driver =>
          signIn(Config.Accounts.curatorUserEmail, Config.Accounts.curatorUserPassword)
          val page = new WorkspaceSummaryPage(namespace, unpubWAttributesName)
          page.open
          assert(page.ui.hasPublishButton)
        }
      }
    }
  }
}



class PublishAsNonCuratorSpec() extends FreeSpec with WebBrowserSpec with BeforeAndAfterAll with BeforeAndAfterEach {

  implicit val ec = ExecutionContext.global

  val unpubName = "unpub-" + UUID.randomUUID.toString + "-Publish"
  val unpubWAttributesName = "unpub-withAttributes" + UUID.randomUUID.toString + "-Publish"
  val namespace = "broad-dsde-dev"

  override def beforeAll(): Unit = {
    // create workspaces
    implicit val authToken = Config.AuthTokens.testFireC
    Orchestration.workspaces.create(namespace, unpubName)
    Orchestration.updateAcl(namespace, unpubName, Config.Accounts.testUserEmail, "WRITER", false)
    Orchestration.workspaces.create(namespace, unpubWAttributesName)
    Orchestration.setLibraryAttributes(namespace, unpubWAttributesName, Library.metadata)
    Orchestration.updateAcl(namespace, unpubWAttributesName, Config.Accounts.testUserEmail, "WRITER", false)
  }

  override def beforeEach(): Unit = {
  }

  override def afterEach(): Unit = {
  }

  override def afterAll(): Unit = {
    implicit val authToken = Config.AuthTokens.testFireC
    Orchestration.workspaces.delete("broad-dsde-dev", unpubName)
    Orchestration.workspaces.delete("broad-dsde-dev", unpubWAttributesName)
  }


  "As a non-curator" - {
    "An unpublished workspace" - {
      "with required org.broadinstitute.dsde.firecloud.library attributes" - {
        "should not be publishable " in withWebDriver { implicit driver =>
          signIn(Config.Accounts.testUserEmail, Config.Accounts.testUserPassword)
          val page = new WorkspaceSummaryPage("broad-dsde-dev", unpubName)
          page.open
          assert(!page.ui.hasPublishButton)
        }
      }
    }
  }
}
