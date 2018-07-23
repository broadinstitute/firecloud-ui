package org.broadinstitute.dsde.firecloud.test.library

import org.broadinstitute.dsde.firecloud.fixture.{LibraryData, UserFixtures}
import org.broadinstitute.dsde.firecloud.page.workspaces.summary.WorkspaceSummaryPage
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.UserPool
import org.broadinstitute.dsde.workbench.fixture.{BillingFixtures, TestReporterFixture, WorkspaceFixtures}
import org.broadinstitute.dsde.workbench.service.test.WebBrowserSpec
import org.scalatest._
import org.scalatest.time.{Millis, Seconds, Span}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}


class UnpublishedWorkspaceSpec extends FreeSpec with ParallelTestExecution with WebBrowserSpec with UserFixtures
  with WorkspaceFixtures with BillingFixtures with Matchers with TestReporterFixture {

  implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  implicit override val patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(10, Seconds)), interval = scaled(Span(500, Millis)))

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
                withSignIn(curatorUser) { _ =>
                  val page = new WorkspaceSummaryPage(billingProject, wsName).open
                  eventually { page.hasPublishButton shouldBe true }
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
