package org.broadinstitute.dsde.firecloud.test.workspace

import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{Credentials, UserPool}
import org.broadinstitute.dsde.workbench.fixture._
import org.broadinstitute.dsde.workbench.service.test.{RandomUtil, WebBrowserSpec}
import org.broadinstitute.dsde.workbench.service.util.Tags
import org.broadinstitute.dsde.workbench.service.{AclEntry, WorkspaceAccessLevel}
import org.scalatest._


/**
  * June 3, 2018
  *   Cannot use `with ParalleTestExecution` because data interference. Error comes from api.methods.setMethodPermissions
  */

class WorkspaceWriterSpec extends FreeSpec with Matchers with WebBrowserSpec with TestReporterFixture
  with WorkspaceFixtures with UserFixtures with MethodFixtures with BillingFixtures with RandomUtil {

  val projectOwner: Credentials = UserPool.chooseProjectOwner
  val authTokenOwner: AuthToken = projectOwner.makeAuthToken()

  val testAttributes = Map("A-key" -> "A value", "B-key" -> "B value", "C-key" -> "C value")
  val noAccessText = "You do not have access to run analysis."


  "A user" - {
    "who has writer access" - {
      "should only see the estimated monthly storage fee in the Project Cost section of the summary page" taggedAs Tags.SmokeTest in {
        val user = UserPool.chooseStudent
        implicit val authToken: AuthToken = authTokenOwner
        val testName = "WorkspaceWriterAccess"
        withCleanBillingProject(projectOwner) { billingProject =>
          withWorkspace(billingProject, testName, Set.empty, List(AclEntry(user.email, WorkspaceAccessLevel.Writer, Some(false), Some(false)))) { workspaceName =>
            withWebDriver { implicit driver =>
              withSignIn(user) { listPage =>
                val workspacePage = listPage.enterWorkspace(billingProject, workspaceName)
                workspacePage.hasGoogleBillingLink shouldBe false
                workspacePage.hasStorageCostEstimate shouldBe true
              }
            }
          }
        }
      }

      "and does not have canCompute permission" - {
        "should see launch analysis button disabled" in {
          val user = UserPool.chooseStudent
          val testName = "WorkspaceWithoutCompute"
          withMethod("m", MethodData.SimpleMethod) { methodName =>
            val methodConfigName = methodName
            api.methods.setMethodPermissions(MethodData.SimpleMethod.methodNamespace, methodName, 1, user.email, "READER")(authTokenOwner)
            withCleanBillingProject(projectOwner) { billingProject =>
              withWorkspace(billingProject, testName, Set.empty, List(AclEntry(user.email, WorkspaceAccessLevel.Writer, Some(false), Some(false)))) { workspaceName =>
                api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)(authTokenOwner)

                api.methodConfigurations.createMethodConfigInWorkspace(billingProject, workspaceName, MethodData.SimpleMethod.copy(methodName = methodName),
                  SimpleMethodConfig.configNamespace, methodConfigName, 1,
                  SimpleMethodConfig.inputs, SimpleMethodConfig.outputs, "participant")(authTokenOwner)

                withWebDriver { implicit driver =>
                  withSignIn(user) { listPage =>
                    val workspacePage = listPage.enterWorkspace(billingProject, workspaceName)
                    val methodConfigDetailsPage = workspacePage.goToMethodConfigTab().openMethodConfig(SimpleMethodConfig.configNamespace, methodConfigName)
                    val errorModal = methodConfigDetailsPage.clickLaunchAnalysisButtonError()
                    errorModal.getMessageText shouldBe noAccessText
                    errorModal.clickOk()
                  }
                }
              }(authTokenOwner)
            }
          }(authTokenOwner)
        }
      }
      "and does have canCompute permission" - {
        "should be able to launch analysis" in {
          val user = UserPool.chooseStudent
          implicit val authToken: AuthToken = user.makeAuthToken()

          val testName = "WorkspaceWithCompute"
          withMethod("m", MethodData.SimpleMethod) { methodName =>
            val methodConfigName = methodName
            api.methods.setMethodPermissions(MethodData.SimpleMethod.methodNamespace, methodName, 1, user.email, "READER")(authTokenOwner)
            withCleanBillingProject(projectOwner) { billingProject =>
              withWorkspace(billingProject, testName, Set.empty, List(AclEntry(user.email, WorkspaceAccessLevel.Writer, Some(false), Some(true)))) { workspaceName =>
                api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)

                api.methodConfigurations.createMethodConfigInWorkspace(billingProject, workspaceName, MethodData.SimpleMethod.copy(methodName = methodName),
                  SimpleMethodConfig.configNamespace, methodConfigName, 1,
                  SimpleMethodConfig.inputs, SimpleMethodConfig.outputs, "participant")

                withWebDriver { implicit driver =>
                  withSignIn(user) { listPage =>
                    val workspacePage = listPage.enterWorkspace(billingProject, workspaceName)
                    val methodConfigDetailsPage = workspacePage.goToMethodConfigTab().openMethodConfig(SimpleMethodConfig.configNamespace, methodConfigName)
                    val launchAnalysisModal = methodConfigDetailsPage.openLaunchAnalysisModal()
                    launchAnalysisModal.validateLocation shouldBe true
                    launchAnalysisModal.clickCancel()
                  }
                }
              }(authTokenOwner)
            }
          }(authTokenOwner)
        }
      }
    }
  }
}
