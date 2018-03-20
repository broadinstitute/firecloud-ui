package org.broadinstitute.dsde.firecloud.test.workspace

import org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs.WorkspaceMethodConfigListPage
import org.broadinstitute.dsde.firecloud.page.workspaces.summary.WorkspaceSummaryPage
import org.broadinstitute.dsde.firecloud.test.Tags
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.UserPool 
import org.broadinstitute.dsde.workbench.fixture.MethodData.SimpleMethod
import org.broadinstitute.dsde.workbench.fixture._
import org.broadinstitute.dsde.workbench.service.{AclEntry, WorkspaceAccessLevel}

class WorkspaceSpec extends WorkspaceSpecBase {

  val noAccessText = "You do not have access to run analysis."

  "Persisted workspace table handles extra columns in storage gracefully" in withWebDriver { implicit driver =>
    val user = UserPool.chooseAnyUser
    implicit val authToken = user.makeAuthToken()
    withWorkspace(billingProject, "Dummy_workspace") { _ =>
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

  "A user" - {
    "with a billing project" - {
      "should be able to create a workspace" taggedAs Tags.SmokeTest in withWebDriver { implicit driver =>
        val user = UserPool.chooseStudent
        implicit val authToken: AuthToken = user.makeAuthToken()
        withSignIn(user) { listPage =>
          val workspaceName = "WorkspaceSpec_create_" + randomUuid
          register cleanUp api.workspaces.delete(billingProject, workspaceName)
          val detailPage = listPage.createWorkspace(billingProject, workspaceName)

          detailPage.validateWorkspace shouldEqual true

          listPage.open
          listPage.hasWorkspace(billingProject, workspaceName) shouldBe true
        }

      }

      "should be able to clone a workspace" in withWebDriver { implicit driver =>
        val user = UserPool.chooseStudent
        implicit val authToken: AuthToken = user.makeAuthToken()
        withWorkspace(billingProject, "WorkspaceSpec_to_be_cloned") { workspaceName =>
          withSignIn(user) { listPage =>
            val workspaceNameCloned = "WorkspaceSpec_clone_" + randomUuid
            val workspaceSummaryPage = new WorkspaceSummaryPage(billingProject, workspaceName).open
            register cleanUp api.workspaces.delete(billingProject, workspaceNameCloned)
            workspaceSummaryPage.cloneWorkspace(billingProject, workspaceNameCloned)

            listPage.open
            listPage.hasWorkspace(billingProject, workspaceNameCloned) shouldBe true}
        }
      }
    }

    "who has reader access to workspace" - {

      "should see launch analysis button disabled" in withWebDriver { implicit driver =>
        val user = UserPool.chooseStudent
        implicit val authToken: AuthToken = authTokenOwner
        withWorkspace(billingProject, "WorkspaceSpec_readAccess", Set.empty, List(AclEntry(user.email, WorkspaceAccessLevel.withName("READER")))) { workspaceName =>
          api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)

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

      "should see import config button disabled" in withWebDriver { implicit driver =>
        val user = UserPool.chooseStudent
        implicit val authToken: AuthToken = authTokenOwner
        withWorkspace(billingProject, "WorkspaceSpec_readAccess", Set.empty, List(AclEntry(user.email, WorkspaceAccessLevel.withName("READER")))) { workspaceName =>
          withSignIn(user) { _ =>
            val methodConfigListPage = new WorkspaceMethodConfigListPage(billingProject, workspaceName).open
            methodConfigListPage.importConfigButtonEnabled() shouldBe false
          }
        }
      }

      "should not see any of the Project Cost section of the summary page" in withWebDriver { implicit driver =>
        val user = UserPool.chooseStudent
        implicit val authToken: AuthToken = authTokenOwner
        val testName = "WorkspaceSpec_writerAccess_projectCost"
        withWorkspace(billingProject, testName, Set.empty, List(AclEntry(user.email, WorkspaceAccessLevel.Reader, Some(false), Some(false)))) { workspaceName =>
          withSignIn(user) { listPage =>
            val workspacePage = listPage.enterWorkspace(billingProject, workspaceName)
            workspacePage.hasGoogleBillingLink shouldBe false
            workspacePage.hasStorageCostEstimate shouldBe false
          }
        }
      }
    }

    "who has writer access" - {

      "should only see the estimated monthly storage fee in the Project Cost section of the summary page" in withWebDriver { implicit driver =>
        val user = UserPool.chooseStudent
        implicit val authToken: AuthToken = authTokenOwner
        val testName = "WorkspaceSpec_writerAccess_projectCost"
        withWorkspace(billingProject, testName, Set.empty, List(AclEntry(user.email, WorkspaceAccessLevel.Writer, Some(false), Some(false)))) { workspaceName =>
          withSignIn(user) { listPage =>
            val workspacePage = listPage.enterWorkspace(billingProject, workspaceName)
            workspacePage.hasGoogleBillingLink shouldBe false
            workspacePage.hasStorageCostEstimate shouldBe true
          }
        }
      }

      "and does not have canCompute permission" - {
        "should see launch analysis button disabled" in withWebDriver { implicit driver =>
          val user = UserPool.chooseStudent
          implicit val authToken: AuthToken = authTokenOwner
          val testName = "WorkspaceSpec_writerAccess_withCompute"
          withMethod(testName, MethodData.SimpleMethod) { methodName =>
            val methodConfigName = methodName + "Config"
            api.methods.setMethodPermissions(MethodData.SimpleMethod.methodNamespace, methodName, 1, user.email, "READER")(authTokenOwner)
            withWorkspace(billingProject, testName, Set.empty, List(AclEntry(user.email, WorkspaceAccessLevel.Writer, Some(false), Some(false)))) { workspaceName =>
              api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)

              api.methodConfigurations.createMethodConfigInWorkspace(billingProject, workspaceName, MethodData.SimpleMethod.copy(methodName = methodName),
                SimpleMethodConfig.configNamespace, methodConfigName, 1,
                SimpleMethodConfig.inputs, SimpleMethodConfig.outputs, "participant")
              withSignIn(user) { listPage =>
                val workspacePage = listPage.enterWorkspace(billingProject, workspaceName)
                val methodConfigDetailsPage = workspacePage.goToMethodConfigTab().openMethodConfig(SimpleMethodConfig.configNamespace, methodConfigName)
                val errorModal = methodConfigDetailsPage.clickLaunchAnalysisButtonError()
                errorModal.getMessageText shouldBe noAccessText
                errorModal.clickOk()
              }
            }(authTokenOwner)
          }(authTokenOwner)
        }
      }
      "and does have canCompute permission" - {
        "should be able to launch analysis" in withWebDriver { implicit driver =>
          val user = UserPool.chooseStudent
          implicit val authToken: AuthToken = user.makeAuthToken()
          val testName = "WorkspaceSpec_writerAccess_withCompute"
          withMethod(testName, MethodData.SimpleMethod) { methodName =>
            val methodConfigName = methodName + "Config"
            api.methods.setMethodPermissions(MethodData.SimpleMethod.methodNamespace, methodName, 1, user.email, "READER")(authTokenOwner)
            withWorkspace(billingProject, testName, Set.empty, List(AclEntry(user.email, WorkspaceAccessLevel.Writer, Some(false), Some(true)))) { workspaceName =>
              api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)

              api.methodConfigurations.createMethodConfigInWorkspace(billingProject, workspaceName, MethodData.SimpleMethod.copy(methodName = methodName),
                SimpleMethodConfig.configNamespace, methodConfigName, 1,
                SimpleMethodConfig.inputs, SimpleMethodConfig.outputs, "participant")
              withSignIn(user) { listPage =>
                api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)
                val workspacePage = listPage.enterWorkspace(billingProject, workspaceName)
                val methodConfigDetailsPage = workspacePage.goToMethodConfigTab().openMethodConfig(SimpleMethodConfig.configNamespace, methodConfigName)
                val launchAnalysisModal = methodConfigDetailsPage.openLaunchAnalysisModal()
                launchAnalysisModal.validateLocation shouldBe true
                launchAnalysisModal.clickCancel()
              }
            }(authTokenOwner)
          }(authTokenOwner)
        }
      }
    }
  }

}
