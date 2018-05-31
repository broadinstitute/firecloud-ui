package org.broadinstitute.dsde.firecloud.test.analysis

import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.UserPool
import org.broadinstitute.dsde.workbench.fixture._
import org.broadinstitute.dsde.workbench.service.test.WebBrowserSpec
import org.scalatest._

class MethodImportSpec extends FreeSpec with ParallelTestExecution with Matchers with WebBrowserSpec with WorkspaceFixtures
  with UserFixtures with MethodFixtures with BillingFixtures {

  "import method config" - {
    "copy from a workspace" in {
      val user = UserPool.chooseProjectOwner
      implicit val authToken: AuthToken = user.makeAuthToken()
      withCleanBillingProject(user) { billingProject =>
        withWorkspace(billingProject, "MethodImportSpec_workspace_src") { sourceWorkspaceName =>
          withWorkspace(billingProject, "MethodImportSpec_workspace_dest") { destWorkspaceName =>
            withMethod("MethodImportSpec_import_from_workspace", MethodData.SimpleMethod, 1) { methodName =>
              val method = MethodData.SimpleMethod.copy(methodName = methodName)
              api.methodConfigurations.createMethodConfigInWorkspace(billingProject, sourceWorkspaceName,
                method, method.methodNamespace, method.methodName, 1, Map.empty, Map.empty, method.rootEntityType)

              withWebDriver { implicit driver =>
                withSignIn(user) { listPage =>
                  api.workspaces.waitForBucketReadAccess(billingProject, destWorkspaceName)
                  val methodConfigTab = listPage.enterWorkspace(billingProject, destWorkspaceName).goToMethodConfigTab()

                  val methodConfigDetailsPage = methodConfigTab.copyMethodConfigFromWorkspace(
                    billingProject, sourceWorkspaceName, method.methodNamespace, method.methodName)

                  methodConfigDetailsPage.isLoaded shouldBe true
                  methodConfigDetailsPage.methodConfigName shouldBe method.methodName

                  // launch modal shows no default entities
                  val launchModal = methodConfigDetailsPage.openLaunchAnalysisModal()
                  launchModal.verifyNoRowsMessage() shouldBe true
                  launchModal.xOut()
                }
              }
            }
          }
        }
      }
    }

    "import from method repo" in {
      val user = UserPool.chooseProjectOwner
      implicit val authToken: AuthToken = user.makeAuthToken()
      withCleanBillingProject(user) { billingProject =>
        withWorkspace(billingProject, "MethodImportSpec_import_from_methodrepo") { workspaceName =>
          withWebDriver { implicit driver =>
            withSignIn(user) { workspaceListPage =>
              val methodConfigPage = workspaceListPage.enterWorkspace(billingProject, workspaceName).goToMethodConfigTab()

              val methodConfigDetailsPage = methodConfigPage.importMethodConfigFromRepo(
                MethodData.SimpleMethod.methodNamespace,
                MethodData.SimpleMethod.methodName,
                MethodData.SimpleMethod.snapshotId,
                SimpleMethodConfig.configName)

              methodConfigDetailsPage.isLoaded shouldBe true
              methodConfigDetailsPage.editMethodConfig(inputs = Some(SimpleMethodConfig.inputs))
            }
          }
        }
      }
    }
  }

}
