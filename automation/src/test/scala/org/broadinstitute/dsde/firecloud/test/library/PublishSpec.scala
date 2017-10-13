package org.broadinstitute.dsde.firecloud.test.library

import org.broadinstitute.dsde.firecloud.page._
import org.broadinstitute.dsde.firecloud.config.{AuthToken, Config}
import org.broadinstitute.dsde.firecloud.fixture.{LibraryData, WorkspaceFixtures, UserFixtures}
import org.broadinstitute.dsde.firecloud.page.library.DataLibraryPage
import org.broadinstitute.dsde.firecloud.page.workspaces.summary.WorkspaceSummaryPage
import org.broadinstitute.dsde.firecloud.test.{CleanUp, WebBrowserSpec, Tags}
import org.broadinstitute.dsde.firecloud.util.Retry.retry
import org.scalatest._
import scala.concurrent.duration.DurationLong

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}


class PublishSpec extends FreeSpec with WebBrowserSpec with UserFixtures with WorkspaceFixtures with CleanUp with Matchers {
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  val namespace: String = Config.Projects.default
  implicit val curatorAuthToken = AuthToken(Config.Users.curator)
  val ronAuthToken = AuthToken(Config.Users.ron)


  "For a user with publish permissions" - {
    "an unpublished workspace" - {
      "without required library attributes" - {
        "publish button should be visible but should open error modal when clicked" in withWebDriver { implicit driver =>
          withWorkspace(namespace, "PublishSpec_curator_unpub_") { wsName =>
            withSignIn(Config.Users.curator) { wsList =>
              val page = new WorkspaceSummaryPage(namespace, wsName).open
              page.clickPublishButton()
              val messageModal = MessageModal()
              messageModal.validateLocation shouldBe true
            }
          }
        }
      }
      "with required library attributes" - {
        "publish button should be visible " in withWebDriver { implicit driver =>
          withWorkspace(namespace, "PublishSpec_curator_unpub_withAttributes_") { wsName =>
            api.library.setLibraryAttributes(namespace, wsName, LibraryData.metadata)
            withSignIn(Config.Users.curator) { wsList =>
              val page = new WorkspaceSummaryPage(namespace, wsName).open
              page.hasPublishButton shouldBe true
            }
          }
        }
      }
    }
    "a published workspace" - {
      "should be visible in the library table" in withWebDriver { implicit driver =>
        withWorkspace(namespace, "PublishSpec_curator_publish_") { wsName =>
          withCleanUp {
            val data = LibraryData.metadata + ("library:datasetName" -> wsName)
            api.library.setLibraryAttributes(namespace, wsName, data)
            register cleanUp api.library.unpublishWorkspace(namespace, wsName)
            api.library.publishWorkspace(namespace, wsName)
            withSignIn(Config.Users.curator) { _ =>
              val page = new DataLibraryPage().open
              page.hasDataset(wsName) shouldBe true
            }
          }
        }
      }
      "should be able to be unpublished" in withWebDriver { implicit driver =>
        withWorkspace(namespace, "PublishSpec_curator_unpublish_") { wsName =>
          withCleanUp {
            val data = LibraryData.metadata + ("library:datasetName" -> wsName)
            api.library.setLibraryAttributes(namespace, wsName, data)
            register cleanUp api.library.unpublishWorkspace(namespace, wsName)
            api.library.publishWorkspace(namespace, wsName)
            withSignIn(Config.Users.curator) { _ =>
              val wspage = new WorkspaceSummaryPage(namespace, wsName).open
              wspage.unpublishWorkspace()

              // Micro-sleep to keep the test from failing (let Elasticsearch catch up?)
              //            Thread sleep 500

              retry[Boolean](100.milliseconds, 1.minute)({
                val libraryPage = new DataLibraryPage().open
                if (libraryPage.hasDataset(wsName))
                  None
                else Some(false)
              }) match {
                case None => fail()
                case Some(s) => s shouldBe false
              }
            }

          }
        }
      }
    }
  }

  "As a non-curator" - {
    "an unpublished workspace" - {
      "with required library attributes" - {
        "should not see publish button " in withWebDriver { implicit driver =>
          withWorkspace(namespace, "PublishSpec_unpub_withAttributes_") { wsName =>
            api.library.setLibraryAttributes(namespace, wsName, LibraryData.metadata)(ronAuthToken)
            withSignIn(Config.Users.ron) { wsList =>
              val page = new WorkspaceSummaryPage(namespace, wsName).open
              page.hasPublishButton shouldBe false
            }
          }(ronAuthToken)
        }
      }
    }
  }
}
