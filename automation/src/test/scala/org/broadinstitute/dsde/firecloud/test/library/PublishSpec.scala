package org.broadinstitute.dsde.firecloud.test.library

import org.broadinstitute.dsde.firecloud.fixture.{LibraryData, UserFixtures}
import org.broadinstitute.dsde.workbench.service.Orchestration
import org.broadinstitute.dsde.firecloud.page.library.DataLibraryPage
import org.broadinstitute.dsde.firecloud.page.workspaces.summary.WorkspaceSummaryPage
import org.broadinstitute.dsde.firecloud.test.Tags
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{Config, UserPool}
import org.broadinstitute.dsde.workbench.fixture.{BillingFixtures, WorkspaceFixtures}
import org.broadinstitute.dsde.workbench.service.test.{CleanUp, WebBrowserSpec}
import org.broadinstitute.dsde.workbench.service.util.Retry.retry
import org.scalatest._

import scala.concurrent.duration.DurationLong
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}


class PublishSpec extends FreeSpec with WebBrowserSpec with UserFixtures with WorkspaceFixtures with BillingFixtures with CleanUp with Matchers {
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  val autocompleteTextQueryPrefix: String = "cance"  // partial string of "cancer" to test autocomplete
  val minNumOfResults: Int = 5

  "For a user with publish permissions" - {
    "an unpublished workspace" - {
      "without required library attributes" - {
        "publish button should be visible but should open error modal when clicked" in withWebDriver { implicit driver =>
          val curatorUser = UserPool.chooseCurator
          implicit val curatorAuthToken: AuthToken = curatorUser.makeAuthToken()
          withCleanBillingProject(curatorUser) { namespace =>
            withWorkspace(namespace, "PublishSpec_curator_unpub_") { wsName =>
              withSignIn(curatorUser) { _ =>
                val page = new WorkspaceSummaryPage(namespace, wsName).open
                val messageModal = page.clickPublishButton(expectSuccess = false)
                messageModal.isVisible shouldBe true
                messageModal.clickOk()
              }
            }
          }
        }
      }
      "with required library attributes" - {
        "publish button should be visible " in withWebDriver { implicit driver =>
          val curatorUser = UserPool.chooseCurator
          implicit val curatorAuthToken: AuthToken = curatorUser.makeAuthToken()
          withCleanBillingProject(curatorUser) { namespace =>
            withWorkspace(namespace, "PublishSpec_curator_unpub_withAttributes_") { wsName =>
              api.library.setLibraryAttributes(namespace, wsName, LibraryData.metadataBasic)
              withSignIn(curatorUser) { wsList =>
                val page = new WorkspaceSummaryPage(namespace, wsName).open
                page.hasPublishButton shouldBe true
              }
            }
          }
        }
      }
    }
    "a published workspace" - {
      "should be visible in the library table" taggedAs Tags.SmokeTest in withWebDriver { implicit driver =>
        val curatorUser = UserPool.chooseCurator
        implicit val curatorAuthToken: AuthToken = curatorUser.makeAuthToken()
        withCleanBillingProject(curatorUser) { namespace =>
          withWorkspace(namespace, "PublishSpec_curator_publish_") { wsName =>
            withCleanUp {
              val data = LibraryData.metadataBasic + ("library:datasetName" -> wsName)
              api.library.setLibraryAttributes(namespace, wsName, data)
              register cleanUp api.library.unpublishWorkspace(namespace, wsName)
              api.library.publishWorkspace(namespace, wsName)
              withSignIn(curatorUser) { _ =>
                val page = new DataLibraryPage().open
                page.doSearch(wsName)
                page.hasDataset(wsName) shouldBe true
              }
            }
          }
        }
      }
      "should be able to be unpublished" in withWebDriver { implicit driver =>
        val curatorUser = UserPool.chooseCurator
        implicit val curatorAuthToken: AuthToken = curatorUser.makeAuthToken()
        withCleanBillingProject(curatorUser) { namespace =>
          withWorkspace(namespace, "PublishSpec_curator_unpublish_") { wsName =>
            withCleanUp {
              val data = LibraryData.metadataBasic + ("library:datasetName" -> wsName)
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
                  libraryPage.doSearch(wsName)
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

      "when cloned" - {
        "should be cloned without copying the published status" in withWebDriver { implicit driver =>
          val curatorUser = UserPool.chooseCurator
          implicit val curatorAuthToken: AuthToken = curatorUser.makeAuthToken()
          withCleanBillingProject(curatorUser) { namespace =>
            withWorkspace(namespace, "PublishSpec_curator_cloning_published") { wsName =>
              withCleanUp {
                val data = LibraryData.metadataBasic + ("library:datasetName" -> wsName)
                api.library.setLibraryAttributes(namespace, wsName, data)
                register cleanUp api.library.unpublishWorkspace(namespace, wsName)
                api.library.publishWorkspace(namespace, wsName)
                withSignIn(curatorUser) { _ =>
                  val wspage = new WorkspaceSummaryPage(namespace, wsName).open
                  val clonedWsName = wsName + "_clone"
                  register cleanUp api.workspaces.delete(namespace, clonedWsName)
                  wspage.cloneWorkspace(namespace, clonedWsName)
                  wspage.hasPublishButton shouldBe true // this will fail if the Unpublish button is displayed.
                val page = new DataLibraryPage().open
                  page.doSearch(wsName)
                  page.hasDataset(clonedWsName) shouldBe false
                }
              }
            }
          }
        }
      }

      "a cloned workspace" - {
        "should default to visible to 'all users' in Library Attributes" in withWebDriver { implicit driver =>
          //create/publish a workspace
          val curatorUser = UserPool.chooseCurator
          implicit val curatorAuthToken: AuthToken = curatorUser.makeAuthToken()
          withCleanBillingProject(curatorUser) { namespace =>
            withWorkspace(namespace, "PublishSpec_curator_publish_") { wsName =>
              withCleanUp {
                val data = LibraryData.metadataBasic + ("library:datasetName" -> wsName)
                api.library.setLibraryAttributes(namespace, wsName, data)
                register cleanUp api.library.unpublishWorkspace(namespace, wsName)
                api.library.publishWorkspace(namespace, wsName)
                //clone workspace
                withSignIn(curatorUser) { listPage =>
                  val workspaceNameCloned = "PublishSpec_curator_publish_cloned" + randomUuid
                  val workspaceSummaryPage = new WorkspaceSummaryPage(namespace, wsName).open
                  register cleanUp api.workspaces.delete(namespace, workspaceNameCloned)
                  workspaceSummaryPage.cloneWorkspace(namespace, workspaceNameCloned)
                  //Verify default group "All users".
                  // In UI this is done by opening Dataset of the cloned WS and
                  //navigating to the 3rd page and making sure that value displayed is "All users".
                  //In swagger you make sure that getDiscoverableGroup endpoint shows []
                  val accessGroup = Orchestration.library.getDiscoverableGroups(namespace, workspaceNameCloned)
                  accessGroup.size shouldBe 0
                }
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
          withCleanBillingProject(studentUser) { namespace =>
            withWorkspace(namespace, "PublishSpec_unpub_withAttributes_") { wsName =>
              api.library.setLibraryAttributes(namespace, wsName, LibraryData.metadataBasic)
              withSignIn(studentUser) { _ =>
                val page = new WorkspaceSummaryPage(namespace, wsName).open
                page.hasPublishButton shouldBe false
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
        "should see a information message about getting TCGA access" in withWebDriver { implicit driver =>
          //log in as a Curator and create/publish TCGA workspace
          val curatorUser = UserPool.chooseCurator
          implicit val curatorAuthToken: AuthToken = curatorUser.makeAuthToken()

          api.NIH.refreshUserInNIH(Config.Users.tcgaJsonWebTokenKey) (curatorAuthToken)
          withCleanBillingProject(curatorUser) { namespace =>
            withWorkspace(namespace, "TCGA_", Set(Config.FireCloud.tcgaAuthDomain)) { wsName =>
              withCleanUp {
                val data = LibraryData.metadataBasic + ("library:datasetName" -> wsName)
                api.library.setLibraryAttributes(namespace, wsName, data)
                register cleanUp api.library.unpublishWorkspace(namespace, wsName)
                api.library.publishWorkspace(namespace, wsName)

                //log in as a user with no TCGA access to make sure TCGA info message is displayed to you in Library
                val studentUser = UserPool.chooseStudent
                withSignIn(studentUser) { _ =>
                  val page = new DataLibraryPage().open
                  page.doSearch(wsName)
                  page.hasDataset(wsName) shouldBe true
                  page.openDataset(wsName)
                  //verify that Request Access modal is shown
                  val requestAccessModal = page.RequestAccessModal()
                  requestAccessModal.isVisible shouldBe true
                  //verify that 'access to TCGA' text is being displayed
                  requestAccessModal.getMessageText should include(requestAccessModal.tcgaAccessText)
                  requestAccessModal.clickOk()
                }
              }
            }
          }
        }
      }
    }
  }

  "DUOS autocomplete" - {
    "should give multiple results for a partial word" in withWebDriver { implicit driver =>
      val user = UserPool.chooseAnyUser
      // there is no need for an auth token for this test, except that the api wrapper expects one
      implicit val authToken: AuthToken = user.makeAuthToken()
      val result = api.library.duosAutocomplete(autocompleteTextQueryPrefix)
      val resultCount = autocompleteTextQueryPrefix.r.findAllMatchIn(result).length
      println(resultCount)
      resultCount should be > minNumOfResults
    }
  }
}




