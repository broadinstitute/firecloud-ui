package org.broadinstitute.dsde.firecloud.test.library

import org.broadinstitute.dsde.firecloud.FireCloudConfig
import org.broadinstitute.dsde.firecloud.fixture.{LibraryData, UserFixtures}
import org.broadinstitute.dsde.workbench.service.{AclEntry, Orchestration, WorkspaceAccessLevel}
import org.broadinstitute.dsde.firecloud.page.library.DataLibraryPage
import org.broadinstitute.dsde.firecloud.page.workspaces.summary.WorkspaceSummaryPage
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.UserPool
import org.broadinstitute.dsde.workbench.fixture.{BillingFixtures, WorkspaceFixtures}
import org.broadinstitute.dsde.workbench.service.test.WebBrowserSpec
import org.broadinstitute.dsde.workbench.service.util.Retry
import org.broadinstitute.dsde.workbench.service.util.Tags
import org.scalatest._
import org.scalatest.time.{Millis, Seconds, Span}

import scala.concurrent.duration.DurationLong


class PublishSpec extends FreeSpec with WebBrowserSpec with UserFixtures with WorkspaceFixtures
  with BillingFixtures with Matchers {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(10, Seconds)), interval = scaled(Span(500, Millis)))


  "For a user with publish permissions" - {
    "a published workspace" - {
      "should be visible in the library table" taggedAs Tags.SmokeTest in {
        val curatorUser = UserPool.chooseCurator
        implicit val curatorAuthToken: AuthToken = curatorUser.makeAuthToken()
        withCleanBillingProject(curatorUser) { billingProject =>
          withWorkspace(billingProject, "PublishSpec_curator_publish_") { wsName =>
            val data = LibraryData.metadataBasic + ("library:datasetName" -> wsName)
            api.library.setLibraryAttributes(billingProject, wsName, data)
            api.library.publishWorkspace(billingProject, wsName)

            withWebDriver { implicit driver =>
              withSignIn(curatorUser) { _ =>
                val page = new DataLibraryPage().open
                eventually {
                  page.doSearch(wsName)
                  page.hasDataset(wsName) shouldBe true
                }
              }
            }
          }
        }
      }
      "should be able to be unpublished" in {
        val curatorUser = UserPool.chooseCurator
        implicit val curatorAuthToken: AuthToken = curatorUser.makeAuthToken()
        withCleanBillingProject(curatorUser) { billingProject =>
          withWorkspace(billingProject, "PublishSpec_curator_unpublish_") { wsName =>
            val data = LibraryData.metadataBasic + ("library:datasetName" -> wsName)
            api.library.setLibraryAttributes(billingProject, wsName, data)
            api.library.publishWorkspace(billingProject, wsName)

            withWebDriver { implicit driver =>
              withSignIn(curatorUser) { _ =>
                val wspage = new WorkspaceSummaryPage(billingProject, wsName).open
                wspage.unpublishWorkspace()

                // Micro-sleep to keep the test from failing (let Elasticsearch catch up?)
                //            Thread sleep 500

                Retry.retry[Boolean](100.milliseconds, 1.minute)({
                  val libraryPage = wspage.goToDataLibrary()
                  libraryPage.doSearch(wsName)
                  if (libraryPage.hasDataset(wsName))
                    None
                  else Some(false)
                }) match {
                  case None => fail()
                  case Some(s) => eventually { s shouldBe false }
                }
              }
            }
          }
        }
      }
      "should be visible to a user that has read access to the workspace but is not in the discoverability group" in {
        val curatorUser = UserPool.chooseCurator
        val wsReaderUser = UserPool.chooseStudent
        implicit val curatorAuthToken: AuthToken = curatorUser.makeAuthToken()
        withCleanBillingProject(curatorUser) { billingProject =>
          withWorkspace(billingProject, "PublishSpec_reader_view_", aclEntries = List(AclEntry(wsReaderUser.email, WorkspaceAccessLevel.Reader))) { wsName =>
            val data = LibraryData.metadataBasic + ("library:datasetName" -> wsName)
            api.library.setLibraryAttributes(billingProject, wsName, data)
            api.library.setDiscoverableGroups(billingProject, wsName, List("all_broad_users"))
            api.library.publishWorkspace(billingProject, wsName)

            withWebDriver { implicit driver =>
              withSignIn(wsReaderUser) { _ =>
                val page = new DataLibraryPage().open
                eventually {
                  page.doSearch(wsName)
                  page.hasDataset(wsName) shouldBe true
                }
              }
            }
          }
        }
      }


      "when cloned" - {
        "should be cloned without copying the published status" in {
          val curatorUser = UserPool.chooseCurator
          implicit val curatorAuthToken: AuthToken = curatorUser.makeAuthToken()
          withCleanBillingProject(curatorUser) { billingProject =>
            withWorkspace(billingProject, "PublishSpec_curator_cloning_published") { wsName =>
              val data = LibraryData.metadataBasic + ("library:datasetName" -> wsName)
              api.library.setLibraryAttributes(billingProject, wsName, data)
              api.library.publishWorkspace(billingProject, wsName)

              withWebDriver { implicit driver =>
                withSignIn(curatorUser) { _ =>
                  val wspage = new WorkspaceSummaryPage(billingProject, wsName).open
                  val clonedWsName = wsName + "_clone"
                  register cleanUp api.workspaces.delete(billingProject, clonedWsName)
                  wspage.cloneWorkspace(billingProject, clonedWsName)
                  eventually { wspage.hasPublishButton shouldBe true } // this will fail if the Unpublish button is displayed.
                val page = new DataLibraryPage().open
                  page.doSearch(wsName)
                  eventually { page.hasDataset(clonedWsName) shouldBe false }
                }
              }
            }
          }
        }
      }

      "a cloned workspace" - {
        "should default to visible to 'all users' in Library Attributes" in {
          //create/publish a workspace
          val curatorUser = UserPool.chooseCurator
          implicit val curatorAuthToken: AuthToken = curatorUser.makeAuthToken()
          withCleanBillingProject(curatorUser) { billingProject =>
            withWorkspace(billingProject, "PublishSpec_curator_publish_") { wsName =>
              val data = LibraryData.metadataBasic + ("library:datasetName" -> wsName)
              api.library.setLibraryAttributes(billingProject, wsName, data)
              api.library.setDiscoverableGroups(billingProject, wsName, List("demo_users"))
              api.library.publishWorkspace(billingProject, wsName)

              //clone workspace
              withWebDriver { implicit driver =>
                withSignIn(curatorUser) { _ =>
                  val workspaceNameCloned = "PublishSpec_curator_publish_cloned" + randomUuid
                  val workspaceSummaryPage = new WorkspaceSummaryPage(billingProject, wsName).open
                  register cleanUp api.workspaces.delete(billingProject, workspaceNameCloned)
                  workspaceSummaryPage.cloneWorkspace(billingProject, workspaceNameCloned)
                  //Verify default group "All users".
                  // In UI this is done by opening Dataset of the cloned WS and
                  //navigating to the 3rd page and making sure that value displayed is "All users".
                  //In swagger you make sure that getDiscoverableGroup endpoint shows []
                  val accessGroup = Orchestration.library.getDiscoverableGroups(billingProject, workspaceNameCloned)
                  eventually { accessGroup.size shouldBe 0 }
                }
              }
            }
          }
        }
      }
    }
  }

  "As a user with no TCGA permissions" - {
    "a published workspace" - {
      "with TCGA control access" - {
        "should see a information message about getting TCGA access" in {
          //log in as a Curator and create/publish TCGA workspace
          val curatorUser = UserPool.chooseCurator
          implicit val curatorAuthToken: AuthToken = curatorUser.makeAuthToken()

          api.NIH.refreshUserInNIH(FireCloudConfig.Users.tcgaJsonWebTokenKey) (curatorAuthToken)
          withCleanBillingProject(curatorUser) { billingProject =>
            withWorkspace(billingProject, "TCGA_", Set(FireCloudConfig.FireCloud.tcgaAuthDomain)) { wsName =>
              val data = LibraryData.metadataBasic + ("library:datasetName" -> wsName)
              api.library.setLibraryAttributes(billingProject, wsName, data)
              api.library.publishWorkspace(billingProject, wsName)

              //log in as a user with no TCGA access to make sure TCGA info message is displayed to you in Library
              val studentUser = UserPool.chooseStudent

              withWebDriver { implicit driver =>
                withSignIn(studentUser) { _ =>
                  val page = new DataLibraryPage().open
                  eventually {
                    page.doSearch(wsName)
                    page.hasDataset(wsName) shouldBe true
                  }
                  page.openDataset(wsName)
                  //verify that Request Access modal is shown
                  val requestAccessModal = page.RequestAccessModal()
                  eventually { requestAccessModal.isVisible shouldBe true }
                  //verify that 'access to TCGA' text is being displayed
                  eventually {  requestAccessModal.getMessageText should include(requestAccessModal.tcgaAccessText) }
                  requestAccessModal.clickOk()
                }
              }
            }
          }
        }
      }
    }
  }

}
