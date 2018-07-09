package org.broadinstitute.dsde.firecloud.test.library

import org.broadinstitute.dsde.firecloud.FireCloudConfig
import org.broadinstitute.dsde.firecloud.fixture.{LibraryData, UserFixtures}
import org.broadinstitute.dsde.workbench.service.Orchestration
import org.broadinstitute.dsde.firecloud.page.library.DataLibraryPage
import org.broadinstitute.dsde.firecloud.page.workspaces.summary.WorkspaceSummaryPage
import org.broadinstitute.dsde.firecloud.test.Tags
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.UserPool
import org.broadinstitute.dsde.workbench.fixture.{BillingFixtures, TestReporterFixture, WorkspaceFixtures}
import org.broadinstitute.dsde.workbench.service.test.{CleanUp, WebBrowserSpec}
import org.broadinstitute.dsde.workbench.service.util.Retry.retry
import org.scalatest._
import org.scalatest.time.{Millis, Seconds, Span}

import scala.concurrent.duration.DurationLong
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}


class PublishSpec extends FreeSpec with WebBrowserSpec with UserFixtures with WorkspaceFixtures with BillingFixtures
  with CleanUp with Matchers with TestReporterFixture {

  implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  implicit override val patienceConfig = PatienceConfig(timeout = scaled(Span(10, Seconds)), interval = scaled(Span(500, Millis)))

  val autocompleteTextQueryPrefix: String = "cance"  // partial string of "cancer" to test autocomplete
  val minNumOfResults: Int = 5

  "For a user with publish permissions" - {
    "an unpublished workspace" - {
      "without required library attributes" - {
        "publish button should be visible but should open error modal when clicked" in {
          val curatorUser = UserPool.chooseCurator
          implicit val curatorAuthToken: AuthToken = curatorUser.makeAuthToken()
          withCleanBillingProject(curatorUser) { billingProject =>
            withWorkspace(billingProject, "PublishSpec_curator_unpub_") { wsName =>

              withWebDriver { implicit driver =>
                withSignIn(curatorUser) { _ =>
                  val page = new WorkspaceSummaryPage(billingProject, wsName).open
                  val messageModal = page.clickPublishButton(expectSuccess = false)
                  eventually { messageModal.isVisible shouldBe true }
                  messageModal.clickOk()
                }
              }
            }
          }
        }
      }
      "with required library attributes" - {
        "publish button should be visible " in {
          val curatorUser = UserPool.chooseCurator
          implicit val curatorAuthToken: AuthToken = curatorUser.makeAuthToken()
          withCleanBillingProject(curatorUser) { billingProject =>
            withWorkspace(billingProject, "PublishSpec_curator_unpub_withAttributes_") { wsName =>
              api.library.setLibraryAttributes(billingProject, wsName, LibraryData.metadataBasic)

              withWebDriver { implicit driver =>
                withSignIn(curatorUser) { wsList =>
                  val page = new WorkspaceSummaryPage(billingProject, wsName).open
                  eventually { page.hasPublishButton shouldBe true }
                }
              }
            }
          }
        }
      }
    }
    "a published workspace" - {
      "should be visible in the library table" taggedAs Tags.SmokeTest in {
        val curatorUser = UserPool.chooseCurator
        implicit val curatorAuthToken: AuthToken = curatorUser.makeAuthToken()
        withCleanBillingProject(curatorUser) { billingProject =>
          withWorkspace(billingProject, "PublishSpec_curator_publish_") { wsName =>
            withCleanUp {
              val data = LibraryData.metadataBasic + ("library:datasetName" -> wsName)
              api.library.setLibraryAttributes(billingProject, wsName, data)
              register cleanUp api.library.unpublishWorkspace(billingProject, wsName)
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
      }
      "should be able to be unpublished" in {
        val curatorUser = UserPool.chooseCurator
        implicit val curatorAuthToken: AuthToken = curatorUser.makeAuthToken()
        withCleanBillingProject(curatorUser) { billingProject =>
          withWorkspace(billingProject, "PublishSpec_curator_unpublish_") { wsName =>
            withCleanUp {
              val data = LibraryData.metadataBasic + ("library:datasetName" -> wsName)
              api.library.setLibraryAttributes(billingProject, wsName, data)
              register cleanUp api.library.unpublishWorkspace(billingProject, wsName)
              api.library.publishWorkspace(billingProject, wsName)

              withWebDriver { implicit driver =>
                withSignIn(curatorUser) { _ =>
                  val wspage = new WorkspaceSummaryPage(billingProject, wsName).open
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
                    case Some(s) => eventually { s shouldBe false }
                  }
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
              withCleanUp {
                val data = LibraryData.metadataBasic + ("library:datasetName" -> wsName)
                api.library.setLibraryAttributes(billingProject, wsName, data)
                register cleanUp api.library.unpublishWorkspace(billingProject, wsName)
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
      }

      "a cloned workspace" - {
        "should default to visible to 'all users' in Library Attributes" in {
          //create/publish a workspace
          val curatorUser = UserPool.chooseCurator
          implicit val curatorAuthToken: AuthToken = curatorUser.makeAuthToken()
          withCleanBillingProject(curatorUser) { billingProject =>
            withWorkspace(billingProject, "PublishSpec_curator_publish_") { wsName =>
              withCleanUp {
                val data = LibraryData.metadataBasic + ("library:datasetName" -> wsName)
                api.library.setLibraryAttributes(billingProject, wsName, data)
                register cleanUp api.library.unpublishWorkspace(billingProject, wsName)
                api.library.publishWorkspace(billingProject, wsName)

                //clone workspace
                withWebDriver { implicit driver =>
                  withSignIn(curatorUser) { listPage =>
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
  }
  "As a non-curator" - {
    "an unpublished workspace" - {
      "with required library attributes" - {
        "should not see publish button" in {
          val studentUser = UserPool.chooseStudent
          implicit val studentAuthToken: AuthToken = studentUser.makeAuthToken()
          withCleanBillingProject(studentUser) { billingProject =>
            withWorkspace(billingProject, "PublishSpec_unpub_withAttributes_") { wsName =>
              api.library.setLibraryAttributes(billingProject, wsName, LibraryData.metadataBasic)

              withWebDriver { implicit driver =>
                withSignIn(studentUser) { _ =>
                  val page = new WorkspaceSummaryPage(billingProject, wsName).open
                  eventually { page.hasPublishButton shouldBe false }
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
              withCleanUp {
                val data = LibraryData.metadataBasic + ("library:datasetName" -> wsName)
                api.library.setLibraryAttributes(billingProject, wsName, data)
                register cleanUp api.library.unpublishWorkspace(billingProject, wsName)
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

  "DUOS autocomplete" - {
    "should give multiple results for a partial word" in {
      val user = UserPool.chooseAnyUser
      // there is no need for an auth token for this test, except that the api wrapper expects one
      implicit val authToken: AuthToken = user.makeAuthToken()
      eventually {
        val result = api.library.duosAutocomplete(autocompleteTextQueryPrefix)
        val resultCount = autocompleteTextQueryPrefix.r.findAllMatchIn(result).length
        resultCount should be > minNumOfResults
      }
    }
  }

}
