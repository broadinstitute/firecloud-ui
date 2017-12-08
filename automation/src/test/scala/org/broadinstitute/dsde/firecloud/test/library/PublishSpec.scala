package org.broadinstitute.dsde.firecloud.test.library

import org.broadinstitute.dsde.firecloud.auth.{AuthToken, UserAuthToken}
import org.broadinstitute.dsde.firecloud.page._
import org.broadinstitute.dsde.firecloud.config.{Config, Credentials, UserPool}
import org.broadinstitute.dsde.firecloud.fixture.{LibraryData, UserFixtures, WorkspaceFixtures}
import org.broadinstitute.dsde.firecloud.page.library.DataLibraryPage
import org.broadinstitute.dsde.firecloud.page.workspaces.summary.WorkspaceSummaryPage
import org.broadinstitute.dsde.firecloud.test.{CleanUp, WebBrowserSpec}
import org.broadinstitute.dsde.firecloud.util.Retry.retry
import org.scalatest._

import scala.concurrent.duration.DurationLong
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}


class PublishSpec extends FreeSpec with WebBrowserSpec with UserFixtures with WorkspaceFixtures with CleanUp with Matchers {
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  val namespace: String = Config.Projects.default

  "For a user with publish permissions" - {
    "an unpublished workspace" - {
      "without required library attributes" - {
        "publish button should be visible but should open error modal when clicked" in withWebDriver { implicit driver =>
          val curatorUser = UserPool.chooseCurator
          implicit val curatorAuthToken: AuthToken = curatorUser.makeAuthToken()
          withWorkspace(namespace, "PublishSpec_curator_unpub_") { wsName =>
            withSignIn(curatorUser) { _ =>
              val page = new WorkspaceSummaryPage(namespace, wsName).open
              page.clickPublishButton()
              val messageModal = MessageModal()
              messageModal.validateLocation shouldBe true
              messageModal.clickCancel()
            }
          }
        }
      }
      "with required library attributes" - {
        "publish button should be visible " in withWebDriver { implicit driver =>
          val curatorUser = UserPool.chooseCurator
          implicit val curatorAuthToken: AuthToken = curatorUser.makeAuthToken()
          withWorkspace(namespace, "PublishSpec_curator_unpub_withAttributes_") { wsName =>
            api.library.setLibraryAttributes(namespace, wsName, LibraryData.metadata)
            withSignIn(curatorUser) { wsList =>
              val page = new WorkspaceSummaryPage(namespace, wsName).open
              page.hasPublishButton shouldBe true
            }
          }
        }
      }
    }
    "a published workspace" - {
      "should be visible in the library table" in withWebDriver { implicit driver =>
        val curatorUser = UserPool.chooseCurator
        implicit val curatorAuthToken: AuthToken = curatorUser.makeAuthToken()
        withWorkspace(namespace, "PublishSpec_curator_publish_") { wsName =>
          withCleanUp {
            val data = LibraryData.metadata + ("library:datasetName" -> wsName)
            api.library.setLibraryAttributes(namespace, wsName, data)
            register cleanUp api.library.unpublishWorkspace(namespace, wsName)
            api.library.publishWorkspace(namespace, wsName)
            withSignIn(curatorUser) { _ =>
              val page = new DataLibraryPage().open
              page.hasDataset(wsName) shouldBe true
            }
          }
        }
      }
      "should be able to be unpublished" in withWebDriver { implicit driver =>
        val curatorUser = UserPool.chooseCurator
        implicit val curatorAuthToken: AuthToken = curatorUser.makeAuthToken()
        withWorkspace(namespace, "PublishSpec_curator_unpublish_") { wsName =>
          withCleanUp {
            val data = LibraryData.metadata + ("library:datasetName" -> wsName)
            api.library.setLibraryAttributes(namespace, wsName, data)
            register cleanUp api.library.unpublishWorkspace(namespace, wsName)
            api.library.publishWorkspace(namespace, wsName)
            withSignIn(curatorUser) { _ =>
              val wspage = new WorkspaceSummaryPage(namespace, wsName).open
              wspage.unpublishWorkspace()

              // Micro-sleep to keep the test from failing (let Elasticsearch catch up?)
              //            Thread sleep 500

              retry[Boolean](100.milliseconds, 1.minute)({
                val libraryPage = wspage.goToDataLibrary()
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
        "should not see publish button" in withWebDriver { implicit driver =>
          val studentUser = UserPool.chooseStudent
          implicit val studentAuthToken: AuthToken = studentUser.makeAuthToken()
          withWorkspace(namespace, "PublishSpec_unpub_withAttributes_") { wsName =>
            api.library.setLibraryAttributes(namespace, wsName, LibraryData.metadata)
            withSignIn(studentUser) { _ =>
              val page = new WorkspaceSummaryPage(namespace, wsName).open
              page.hasPublishButton shouldBe false
            }
          }
        }
      }
    }

  }

  "As a user with no TCGA permissions" - {
    "a published workspace" - {
      "with TCGA control access" - {
        "should see a information message about getting TCGA access" in withWebDriver { implicit driver =>
          //log in as a Curator and create/publish TCGA workspace
          val curatorUser = UserPool.chooseCurator
          implicit val curatorAuthToken: AuthToken = curatorUser.makeAuthToken()

          api.NIH.addUserToNIH(Config.Users.jwt)
          withWorkspace(namespace, "TCGA_", Set(Config.FireCloud.tcgaAuthDomain)) { wsName =>
            withCleanUp {
              val data = LibraryData.metadata + ("library:datasetName" -> wsName)
              api.library.setLibraryAttributes(namespace, wsName, data)
              register cleanUp api.library.unpublishWorkspace(namespace, wsName)
              api.library.publishWorkspace(namespace, wsName)

              //log in as a user with no TCGA access to make sure TCGA info message is displayed to you in Libraryval studentUser = UserPool.chooseStudent
              val studentUser = UserPool.chooseStudent
              withSignIn(studentUser) { _ =>
                val page = new DataLibraryPage().open
                page.hasDataset(wsName) shouldBe true
                page.openDataset(wsName)
                //verify that Request Access modal is shown
                val requestAccessModal   = RequestAccessModal()
                requestAccessModal.validateLocation shouldBe true
                requestAccessModal.getRequestAccessText.contains(requestAccessModal.requestAccessText)
                requestAccessModal.clickOk()

              }


            }
          }
        }
      }
    }
  }
}



