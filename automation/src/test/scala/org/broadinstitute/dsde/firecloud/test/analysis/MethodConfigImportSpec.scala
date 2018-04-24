package org.broadinstitute.dsde.firecloud.test.analysis

import java.util.UUID

import org.broadinstitute.dsde.firecloud.fixture.{TestData, UserFixtures, _}
import org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs.WorkspaceMethodConfigListPage
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.UserPool
import org.broadinstitute.dsde.workbench.fixture._
import org.broadinstitute.dsde.workbench.service.test.WebBrowserSpec
import org.scalatest._


class MethodConfigImportSpec extends FreeSpec with Matchers with WebBrowserSpec with WorkspaceFixtures with UserFixtures with MethodFixtures with BillingFixtures {

  val methodConfigName: String = SimpleMethodConfig.configName + "_" + UUID.randomUUID().toString

  "import a method config from a workspace" in withWebDriver { implicit driver =>
    val user = UserPool.chooseProjectOwner
    implicit val authToken: AuthToken = user.makeAuthToken()
    withCleanBillingProject(user) { billingProject =>
      withWorkspace(billingProject, "Test_copy_method_config_from_workspace_src") { sourceWorkspaceName =>
        withWorkspace(billingProject, "Test_copy_method_config_from_workspace_dest") { destWorkspaceName =>
          val method = MethodData.SimpleMethod
          withMethod("MethodConfigImportSpec_from_workspace", method, 1) { methodName =>
            api.methodConfigurations.createMethodConfigInWorkspace(billingProject, sourceWorkspaceName,
              method.copy(methodName = methodName), method.methodNamespace, method.methodName, 1, Map.empty, Map.empty, method.rootEntityType)

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

  "import a method config into a workspace from the method repo" in withWebDriver { implicit driver =>
    val user = UserPool.chooseProjectOwner
    implicit val authToken: AuthToken = user.makeAuthToken()
    withCleanBillingProject(user) { billingProject =>
      withWorkspace(billingProject, "MethodConfigImportSpec_import_from_methodrepo") { workspaceName =>
        withSignIn(user) { workspaceListPage =>
          val methodConfigPage = workspaceListPage.enterWorkspace(billingProject, workspaceName).goToMethodConfigTab()

          val methodConfigDetailsPage = methodConfigPage.importMethodConfigFromRepo(
            MethodData.SimpleMethod.methodNamespace,
            MethodData.SimpleMethod.methodName,
            MethodData.SimpleMethod.snapshotId,
            SimpleMethodConfig.configName)

          methodConfigDetailsPage.isLoaded shouldBe true
        }
      }
    }
  }

  "import a method into a workspace from the method repo" in withWebDriver { implicit driver =>
    val user = UserPool.chooseProjectOwner
    implicit val authToken: AuthToken = user.makeAuthToken()
    withCleanBillingProject(user) { billingProject =>
      withWorkspace(billingProject, "TestSpec_FireCloud_import_method_from_workspace") { workspaceName =>
        api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)
        withSignIn(user) { _ =>
          val workspaceMethodConfigPage = new WorkspaceMethodConfigListPage(billingProject, workspaceName).open

          val methodConfigDetailsPage = workspaceMethodConfigPage.importMethodConfigFromRepo(
            MethodData.SimpleMethod.methodNamespace,
            MethodData.SimpleMethod.methodName,
            MethodData.SimpleMethod.snapshotId,
            SimpleMethodConfig.configName,
            Some(MethodData.SimpleMethod.rootEntityType))

          methodConfigDetailsPage.editMethodConfig(inputs = Some(SimpleMethodConfig.inputs))

          methodConfigDetailsPage.isLoaded shouldBe true
        }
      }
    }
  }

}
