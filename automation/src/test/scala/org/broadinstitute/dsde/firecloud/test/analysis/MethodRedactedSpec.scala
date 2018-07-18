package org.broadinstitute.dsde.firecloud.test.analysis

import java.util.UUID

import org.broadinstitute.dsde.firecloud.component.MessageModal
import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs.{WorkspaceMethodConfigDetailsPage, WorkspaceMethodConfigListPage}
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{Credentials, UserPool}
import org.broadinstitute.dsde.workbench.fixture._
import org.broadinstitute.dsde.workbench.service.test.WebBrowserSpec
import org.scalatest._
import org.scalatest.concurrent.Eventually

class MethodRedactedSpec extends FreeSpec with Matchers with WebBrowserSpec with WorkspaceFixtures
  with UserFixtures with MethodFixtures with BillingFixtures with Eventually with TestReporterFixture {

  val methodConfigName: String = SimpleMethodConfig.configName + "_" + UUID.randomUUID().toString

  "For a method config that references a redacted method" - {
    "should be able to choose new method snapshot" in {
      val user: Credentials = UserPool.chooseProjectOwner
      implicit val authToken: AuthToken = user.makeAuthToken()
      withCleanBillingProject(user) { billingProject =>
        withWorkspace(billingProject, "MethodRedactedSpec_choose_new_snapshot") { workspaceName =>
          withMethod("MethodRedactedSpec_choose_new_snapshot", MethodData.SimpleMethod, 2) { methodName =>
            val method = MethodData.SimpleMethod.copy(methodName = methodName)
            api.methodConfigurations.createMethodConfigInWorkspace(billingProject, workspaceName, method,
              SimpleMethodConfig.configNamespace, methodConfigName, 1, SimpleMethodConfig.inputs, SimpleMethodConfig.outputs, "participant")
            api.methods.redact(method)

            withWebDriver { implicit driver =>
              withSignIn(user) { _ =>
                val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open
                methodConfigDetailsPage.openEditMode()
                eventually { methodConfigDetailsPage.checkSaveButtonState shouldEqual "disabled" }
                methodConfigDetailsPage.saveEdits(expectSuccess = false)
                val modal = await ready new MessageModal()
                modal.isVisible shouldBe true
                modal.clickOk()

                methodConfigDetailsPage.changeSnapshotId(2)
                methodConfigDetailsPage.saveEdits()
                methodConfigDetailsPage.isSnapshotRedacted shouldBe false
              }
            }
          }
        }
      }
    }

    "launch analysis and delete errors on redacted method" in {
      val user = UserPool.chooseProjectOwner
      implicit val authToken: AuthToken = user.makeAuthToken()
      withCleanBillingProject(user) { billingProject =>
        withWorkspace(billingProject, "MethodRedactedSpec_delete") { workspaceName =>
          withMethod("MethodRedactedSpec_delete", MethodData.SimpleMethod, cleanUp = false) { methodName =>
            val method = MethodData.SimpleMethod.copy(methodName = methodName)
            api.methodConfigurations.createMethodConfigInWorkspace(billingProject, workspaceName, method,
              SimpleMethodConfig.configNamespace, methodConfigName, 1, SimpleMethodConfig.inputs, SimpleMethodConfig.outputs, "participant")
            api.methods.redact(method)

            withWebDriver { implicit driver =>
              withSignIn(user) { _ =>
                // launch analysis error:
                // should have warning icon in config list
                val workspaceMethodConfigPage = new WorkspaceMethodConfigListPage(billingProject, workspaceName).open
                workspaceMethodConfigPage.hasRedactedIcon(methodConfigName) shouldBe true

                // should be disabled and show error if clicked
                val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open
                val errorModal = methodConfigDetailsPage.clickLaunchAnalysisButtonError()
                errorModal.isVisible shouldBe true
                errorModal.clickOk() // dismiss modal

                // should be able to be deleted when no unredacted snapshot exists
                methodConfigDetailsPage.openEditMode(expectSuccess = false)
                val modal = await ready new MessageModal()
                modal.isVisible shouldBe true
                modal.getMessageText should include("There are no available method snapshots")
                modal.clickOk()

                methodConfigDetailsPage.deleteMethodConfig()
                val list = await ready new WorkspaceMethodConfigListPage(billingProject, workspaceName)
                list.hasConfig(methodConfigName) shouldBe false
              }
            }
          }
        }
      }
    }
  }

}
