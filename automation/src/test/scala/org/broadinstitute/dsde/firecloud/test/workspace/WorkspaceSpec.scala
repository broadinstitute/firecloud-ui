package org.broadinstitute.dsde.firecloud.test.workspace

import java.util.UUID

import org.broadinstitute.dsde.firecloud.api.{AclEntry, WorkspaceAccessLevel}
import org.broadinstitute.dsde.firecloud.config.{AuthToken, AuthTokens, Config}
import org.broadinstitute.dsde.firecloud.fixture.MethodData.{SimpleMethod, SimpleMethodConfig}
import org.broadinstitute.dsde.firecloud.fixture.{MethodData, MethodFixtures, TestData, WorkspaceFixtures}
import org.broadinstitute.dsde.firecloud.page.MessageModal
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceSummaryPage
import org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs.{WorkspaceMethodConfigDetailsPage, WorkspaceMethodConfigListPage}
import org.broadinstitute.dsde.firecloud.test.{CleanUp, WebBrowserSpec}
import org.scalatest._

class WorkspaceSpec extends FreeSpec with WebBrowserSpec with WorkspaceFixtures with MethodFixtures
  with CleanUp with Matchers {

  val methodConfigName: String = MethodData.SimpleMethodConfig.configName + "_" + UUID.randomUUID().toString
  implicit val authToken: AuthToken = AuthTokens.harry
  val billingProject: String = Config.Projects.default

  "A user" - {
    "with a billing project" - {
      "should be able to create a workspace" in withWebDriver { implicit driver =>
        val listPage = signIn(Config.Users.harry)

        val workspaceName = "WorkspaceSpec_create_" + randomUuid
        register cleanUp api.workspaces.delete(billingProject, workspaceName)
        val detailPage = listPage.createWorkspace(billingProject, workspaceName).awaitLoaded()

        detailPage.readWorkspace shouldEqual (billingProject, workspaceName)

        listPage.open
        listPage.filter(workspaceName)
        listPage.ui.hasWorkspace(billingProject, workspaceName) shouldBe true
      }

      "should be able to clone a workspace" in withWebDriver { implicit driver =>
        withWorkspace(billingProject, "WorkspaceSpec_to_be_cloned") { workspaceName =>
          val listPage = signIn(Config.Users.harry)

          val workspaceNameCloned = "WorkspaceSpec_clone_" + randomUuid
          val workspaceSummaryPage = new WorkspaceSummaryPage(billingProject, workspaceName).open
          register cleanUp api.workspaces.delete(billingProject, workspaceNameCloned)
          workspaceSummaryPage.cloneWorkspace(billingProject, workspaceNameCloned).awaitLoaded()

          listPage.open
          listPage.filter(workspaceNameCloned)
          listPage.ui.hasWorkspace(billingProject, workspaceNameCloned) shouldBe true
        }
      }
    }

    "who owns a workspace" - {
      "should be able to delete the workspace" in withWebDriver { implicit driver =>
        withWorkspace(billingProject, "WorkspaceSpec_delete") { workspaceName =>
          val listPage = signIn(Config.Users.harry)

          val detailPage = listPage.openWorkspaceDetails(billingProject, workspaceName).awaitLoaded()
          detailPage.deleteWorkspace().awaitLoaded()
          listPage.validateLocation()
          listPage.filter(workspaceName)
          listPage.ui.hasWorkspace(billingProject, workspaceName) shouldBe false
        }
      }

      "should be able to share the workspace" in withWebDriver { implicit driver =>
        withWorkspace(billingProject, "WorkspaceSpec_share") { workspaceName =>
          val listPage = signIn(Config.Users.harry)

          val detailPage = listPage.openWorkspaceDetails(billingProject, workspaceName).awaitLoaded()
          detailPage.share(Config.Users.ron.email, "READER")
          detailPage.signOut()
          val listPage2 = signIn(Config.Users.ron)
          val detailPage2 = listPage2.openWorkspaceDetails(billingProject, workspaceName).awaitLoaded()
          detailPage2.ui.readAccessLevel() shouldBe WorkspaceAccessLevel.Reader
        }
      }

      "should be able to set can share permissions for other (non-owner) users" in withWebDriver {implicit driver =>
        withWorkspace(billingProject, "WorkspaceSpec_share") {workspaceName =>
          val listPage = signIn(Config.Users.harry)

          val detailPage = listPage.openWorkspaceDetails(billingProject, workspaceName).awaitLoaded()
          detailPage.share(Config.Users.ron.email, "READER", true)
          detailPage.signOut()
          val listPage2 = signIn(Config.Users.ron)
          val detailPage2 = listPage2.openWorkspaceDetails(billingProject, workspaceName).awaitLoaded()
          detailPage2.ui.hasShareButton shouldBe true
        }
      }

      "should be able to set can compute permissions for users that are writers" in withWebDriver {implicit driver =>
        withWorkspace(billingProject, "WorkspaceSpec_share") { workspaceName =>
          withConfigForMethodInWorkspace("MethodinWorkspaceSpec", billingProject, workspaceName) { configName =>
            api.methodConfigurations.setMethodConfigPermission("MethodinWorkspaceSpec", SimpleMethod.methodName, 1, Config.Users.ron.email, "OWNER")
            val listPage = signIn(Config.Users.harry)
            val detailPage = listPage.openWorkspaceDetails(billingProject, workspaceName).awaitLoaded()
            val aclEditor = detailPage.openShareDialog(Config.Users.ron.email, "WRITER")
            aclEditor.ui.canComputeEnabled() shouldBe true
            aclEditor.ui.canComputeChecked() shouldBe false
          }
        }
      }

      "should not be able to set/change can compute permissions for other owners" in withWebDriver {implicit driver =>
        withWorkspace(billingProject, "WorkspaceSpec_share") { workspaceName =>
          api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)
          api.methodConfigurations.createMethodConfigInWorkspace(billingProject, workspaceName, 1, SimpleMethod.methodNamespace, methodConfigName, 1, SimpleMethodConfig.configNamespace, s"$methodConfigName Config",
            SimpleMethodConfig.inputs, SimpleMethodConfig.outputs, "participant")
          val listPage = signIn(Config.Users.harry)
          val detailPage = listPage.openWorkspaceDetails(billingProject, workspaceName).awaitLoaded()
          val aclEditor = detailPage.openShareDialog(Config.Users.ron.email, "OWNER")
          aclEditor.ui.canComputeEnabled() shouldBe false
          aclEditor.ui.canComputeChecked() shouldBe true
        }
      }
      //reader permissions should always be false
      "should not be able to set/change compute permissions for readers" in withWebDriver { implicit driver =>
        withWorkspace(billingProject, "WorkspaceSpec_share") { workspaceName =>
          api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)
          api.methodConfigurations.createMethodConfigInWorkspace(billingProject, workspaceName, 1, SimpleMethod.methodNamespace, methodConfigName, 1, SimpleMethodConfig.configNamespace, s"$methodConfigName Config",
            SimpleMethodConfig.inputs, SimpleMethodConfig.outputs, "participant")
          val listPage = signIn(Config.Users.harry)
          val detailPage = listPage.openWorkspaceDetails(billingProject, workspaceName).awaitLoaded()
          val aclEditor = detailPage.openShareDialog(Config.Users.ron.email, "READER")
          aclEditor.ui.canComputeEnabled() shouldBe false
          aclEditor.ui.canComputeChecked() shouldBe false
        }
      }

      "should be able to enter workspace attributes" in withWebDriver { implicit driver =>
        withWorkspace(billingProject, "WorkspaceSpec_add_ws_attrs") { workspaceName =>
          val listPage = signIn(Config.Users.harry)
          val detailPage = listPage.openWorkspaceDetails(billingProject, workspaceName).awaitLoaded()

          detailPage.ui.beginEditing()
          detailPage.ui.addWorkspaceAttribute("a", "a")
          detailPage.ui.addWorkspaceAttribute("b", "b")
          detailPage.ui.addWorkspaceAttribute("c", "c")
          detailPage.ui.save()
          detailPage.awaitLoaded()

          // TODO: ensure sort, for now it's default sorted by key, ascending
          detailPage.ui.readWorkspaceTable shouldBe List(List("a", "a"), List("b", "b"), List("c", "c"))
        }
      }
    }
    "who has reader access to workspace" - {

      "should see launch analysis button disabled" in withWebDriver { implicit driver =>
        withWorkspace(billingProject, "WorkspaceSpec_readAccess", Set.empty, List(AclEntry(Config.Users.ron.email, WorkspaceAccessLevel.withName("READER")))) { workspaceName =>
          withConfigForMethodInWorkspace("MethodinWorkspaceSpec", billingProject, workspaceName) { configName =>
            val listPage = signIn(Config.Users.ron)
            val detailPage = listPage.openWorkspaceDetails(billingProject, workspaceName).awaitLoaded()
            val methodConfigTab = detailPage.ui.clickMethodConfigTab(billingProject, workspaceName)
            val methodConfigDetailsPage = methodConfigTab.openMethodConfig("MethodinWorkspaceSpec", "MethodinWorkspaceSpec")
            val errorModal = methodConfigDetailsPage.ui.clickLaunchAnalysisButtonError()
            errorModal.getErrorText() shouldBe "You do not have access to run analysis.\nCancel"

          }
        }
      }

      "should see import config button disabled" in withWebDriver { implicit driver =>
        withWorkspace(billingProject, "WorkspaceSpec_readAccess", Set.empty, List(AclEntry(Config.Users.ron.email, WorkspaceAccessLevel.withName("READER")))) { workspaceName =>
          val methodConfigListPage = new WorkspaceMethodConfigListPage(billingProject, workspaceName).open
          methodConfigListPage.ui.importConfigButtonEnabled() shouldBe false
        }
      }
    }

    "who has writer access" - {
      "and does not have canCompute permission" - {
        "should see launch analysis button disabled" in withWebDriver { implicit driver =>
          withWorkspace(billingProject, "WorkspaceSpect_writerAccess") { workspaceName =>
            withConfigForMethodInWorkspace("MethodinWorkspaceSpec", billingProject, workspaceName) { configName =>
              api.methodConfigurations.setMethodConfigPermission("MethodinWorkspaceSpec", SimpleMethod.methodName, 1, Config.Users.ron.email, "OWNER")
              val listPage = signIn(Config.Users.harry)
              val detailPage = listPage.openWorkspaceDetails(billingProject, workspaceName).awaitLoaded()
              detailPage.share(Config.Users.ron.email, "WRITER", false, false)
              detailPage.signOut()
              val listPage2 = signIn(Config.Users.ron)
              val detailPage2 = listPage.openWorkspaceDetails(billingProject, workspaceName).awaitLoaded()
              val methodConfigTab = detailPage2.ui.clickMethodConfigTab(billingProject, workspaceName)
              val methodConfigDetailsPage = methodConfigTab.openMethodConfig("MethodinWorkspaceSpec", "MethodinWorkspaceSpec")
              val errorModal = methodConfigDetailsPage.ui.clickLaunchAnalysisButtonError()
              errorModal.getErrorText() shouldBe "You do not have access to run analysis.\nCancel"
            }
          }
        }
      }
      "and does have canCompute permission" - {
        "should be able to launch analysis" in withWebDriver { implicit driver =>
          withWorkspace(billingProject, "WorkspaceSpec_writerAccess") { workspaceName =>
            withConfigForMethodInWorkspace("MethodinWorkspaceSpec", billingProject, workspaceName) { configName =>
              api.methodConfigurations.setMethodConfigPermission("MethodinWorkspaceSpec", SimpleMethod.methodName, 1, Config.Users.ron.email, "OWNER")
              val listPage = signIn(Config.Users.harry)
              val detailPage = listPage.openWorkspaceDetails(billingProject, workspaceName).awaitLoaded()
              detailPage.share(Config.Users.ron.email, "WRITER", false, true)
              detailPage.signOut()
              val listPage2 = signIn(Config.Users.ron)
              val detailPage2 = listPage.openWorkspaceDetails(billingProject, workspaceName).awaitLoaded()
              val methodConfigTab = detailPage2.ui.clickMethodConfigTab(billingProject, workspaceName)
              val methodConfigDetailsPage = methodConfigTab.openMethodConfig("MethodinWorkspaceSpec", "MethodinWorkspaceSpec")
              val launchAnalysisModal = methodConfigDetailsPage.ui.openLaunchAnalysisModal()
              launchAnalysisModal.validateLocation shouldBe true
            }
          }
        }
      }
    }

  }

  // Experimental
  "A cloned workspace" - {
    "should retain the source workspace's authorization domain" in withWebDriver { implicit driver =>
      withClonedWorkspace(Config.Projects.common, "AuthDomainSpec_share") { cloneWorkspaceName =>
        val listPage = signIn(Config.Users.harry)
        val summaryPage = listPage.openWorkspaceDetails(Config.Projects.common, cloneWorkspaceName).awaitLoaded()
        // assert that user who cloned the workspace is the owner
      }
    }
  }
}
