package org.broadinstitute.dsde.firecloud.test.workspace

import java.util.UUID

import org.broadinstitute.dsde.firecloud.fixture.{TestData, UserFixtures}
import org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs.WorkspaceMethodConfigListPage
import org.broadinstitute.dsde.firecloud.page.workspaces.summary.WorkspaceSummaryPage
import org.broadinstitute.dsde.firecloud.test.Tags
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{Credentials, UserPool}
import org.broadinstitute.dsde.workbench.fixture.MethodData.SimpleMethod
import org.broadinstitute.dsde.workbench.fixture._
import org.broadinstitute.dsde.workbench.service.test.{CleanUp, WebBrowserSpec}
import org.broadinstitute.dsde.workbench.service.{AclEntry, RestException, WorkspaceAccessLevel}
import org.scalatest._

import scala.util.Try

class WorkspaceReaderSpec extends FreeSpec with Matchers
  with WebBrowserSpec with CleanUp
  with WorkspaceFixtures with UserFixtures with MethodFixtures with BillingFixtures {

  val projectOwner: Credentials = UserPool.chooseProjectOwner
  val authTokenOwner: AuthToken = projectOwner.makeAuthToken()
  val methodConfigName: String = SimpleMethodConfig
    .configName + "_" + UUID.randomUUID().toString + "Config"

  val testAttributes = Map("A-key" -> "A value", "B-key" -> "B value", "C-key" -> "C value")
  val noAccessText = "You do not have access to run analysis."

  "Persisted workspace table handles extra columns in storage gracefully" in {
    val user = UserPool.chooseAnyUser
    implicit val authToken: AuthToken = user.makeAuthToken()
    withCleanBillingProject(user) { billingProject =>
      withWorkspace(billingProject, "Dummy_workspace") { _ =>
        withWebDriver { implicit driver =>
          withSignIn(user) { listPage =>
            val workspacesTable = listPage.workspacesTable
            // put an invalid column into the list:
            workspacesTable.putStoredValue(
              """{:query-params {:sort-order :asc, :page-number 1, :sort-column "Workspace", :rows-per-page 20, :filter-text ""}, """ +
                """:column-display [{:id "FAKE", :width 100, :visible? true} {:id "Status", :width 56, :visible? true} """ +
                """{:id "Workspace", :width 315, :visible? true} {:id "Description", :width 350, :visible? true} """ +
                """{:id "Last Modified", :width 200, :visible? true} {:id "Access Level", :width 132, :visible? true}], """ +
                """:selected-tab-index 0, :v 3}""")

            // on refresh, page doesn't bomb:
            listPage.open
            workspacesTable.isVisible shouldBe true
            workspacesTable.readColumnHeaders shouldBe List("Status", "Workspace", "Description", "Last Modified", "Access Level")
          }
        }
      }
    }
  }

  "A user" - {
    "who has reader access to workspace" - {
      "should see launch analysis button disabled" in {
        val user = UserPool.chooseStudent
        implicit val authToken: AuthToken = authTokenOwner
        withCleanBillingProject(projectOwner) { billingProject =>
          withWorkspace(billingProject, "WorkspaceSpec_readAccess", Set.empty, List(AclEntry(user.email, WorkspaceAccessLevel.withName("READER")))) { workspaceName =>
            api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)
            withWebDriver { implicit driver =>
              withSignIn(user) { listPage =>
                api.methodConfigurations.createMethodConfigInWorkspace(billingProject, workspaceName, SimpleMethod, SimpleMethodConfig.configNamespace, s"$methodConfigName", 1,
                  SimpleMethodConfig.inputs, SimpleMethodConfig.outputs, "participant")
                val detailPage = listPage.enterWorkspace(billingProject, workspaceName)
                val methodConfigTab = detailPage.goToMethodConfigTab()
                val methodConfigDetailsPage = methodConfigTab.openMethodConfig(SimpleMethodConfig.configNamespace, s"$methodConfigName")
                val messageModal = methodConfigDetailsPage.clickLaunchAnalysisButtonError()
                messageModal.getMessageText shouldBe noAccessText
                messageModal.clickOk()
              }
            }
          }
        }
      }
      "should see import config button disabled" in {
        val user = UserPool.chooseStudent
        implicit val authToken: AuthToken = authTokenOwner
        withCleanBillingProject(projectOwner) { billingProject =>
          withWorkspace(billingProject, "WorkspaceSpec_readAccess", Set.empty, List(AclEntry(user.email, WorkspaceAccessLevel.withName("READER")))) { workspaceName =>
            withWebDriver { implicit driver =>
              withSignIn(user) { _ =>
                val methodConfigListPage = new WorkspaceMethodConfigListPage(billingProject, workspaceName).open
                methodConfigListPage.importConfigButtonEnabled() shouldBe false
              }
            }
          }
        }
      }
      "should not see any of the Project Cost section of the summary page" in {
        val user = UserPool.chooseStudent
        implicit val authToken: AuthToken = authTokenOwner
        val testName = "WorkspaceSpec_writerAccess_projectCost"
        withCleanBillingProject(projectOwner) { billingProject =>
          withWorkspace(billingProject, testName, Set.empty, List(AclEntry(user.email, WorkspaceAccessLevel.Reader, Some(false), Some(false)))) { workspaceName =>
            withWebDriver { implicit driver =>
              withSignIn(user) { listPage =>
                val workspacePage = listPage.enterWorkspace(billingProject, workspaceName)
                workspacePage.hasGoogleBillingLink shouldBe false
                workspacePage.hasStorageCostEstimate shouldBe false
              }
            }
          }
        }
      }
    }
  }
}
