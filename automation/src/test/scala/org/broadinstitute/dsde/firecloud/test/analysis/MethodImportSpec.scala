package org.broadinstitute.dsde.firecloud.test.analysis

import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.UserPool
import org.broadinstitute.dsde.workbench.fixture._
import org.broadinstitute.dsde.workbench.service.test.WebBrowserSpec
import org.scalatest._

class MethodImportSpec extends FreeSpec with Matchers with WebBrowserSpec with WorkspaceFixtures with UserFixtures with MethodFixtures with BillingFixtures {

  "import method config" - {
    "copy from a workspace" in withWebDriver { implicit driver =>
      val user = UserPool.chooseProjectOwner
      implicit val authToken: AuthToken = user.makeAuthToken()
      withCleanBillingProject(user) { billingProject =>
        withWorkspace(billingProject, "Copy_method_config_from_workspace_src") { sourceWorkspaceName =>
          withWorkspace(billingProject, "Copy_method_config_from_workspace_dest") { destWorkspaceName =>
            withMethod("MethodImportSpec_import_from_workspace", MethodData.SimpleMethod, 1) { methodName =>
              val method = MethodData.SimpleMethod.copy(methodName = methodName)
              api.methodConfigurations.createMethodConfigInWorkspace(billingProject, sourceWorkspaceName,
                method, method.methodNamespace, method.methodName, 1, Map.empty, Map.empty, method.rootEntityType)

              withSignIn(user) { listPage =>
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

    "import from method repo" in withWebDriver { implicit driver =>
      val user = UserPool.chooseProjectOwner
      implicit val authToken: AuthToken = user.makeAuthToken()
      withCleanBillingProject(user) { billingProject =>
        withWorkspace(billingProject, "MethodImportSpec_import_from_methodrepo") { workspaceName =>
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
