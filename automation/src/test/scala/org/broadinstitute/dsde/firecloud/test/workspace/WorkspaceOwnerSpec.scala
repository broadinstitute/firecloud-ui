package org.broadinstitute.dsde.firecloud.test.workspace

import org.broadinstitute.dsde.firecloud.fixture.TestData
import org.broadinstitute.dsde.firecloud.test.Tags
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.UserPool
import org.broadinstitute.dsde.workbench.fixture.MethodData.SimpleMethod
import org.broadinstitute.dsde.workbench.fixture.SimpleMethodConfig
import org.broadinstitute.dsde.workbench.service.{RestException, WorkspaceAccessLevel}

import scala.util.Try

class WorkspaceOwnerSpec extends WorkspaceSpecBase {

  val testAttributes = Map("A-key" -> "A value", "B-key" -> "B value", "C-key" -> "C value")

  "A user" - {

    "who owns a workspace" - {
      "should be able to delete the workspace" taggedAs Tags.SmokeTest in withWebDriver { implicit driver =>
        val user = UserPool.chooseStudent
        implicit val authToken: AuthToken = user.makeAuthToken()
        withWorkspace(billingProject, "WorkspaceSpec_delete", cleanUp = false) { workspaceName =>
          // special cleanup because it is expected to fail as it should already be deleted
          register cleanUp Try(api.workspaces.delete(billingProject, workspaceName)).recover {
            case _: RestException =>
          }

          withSignIn(user) { listPage =>
            val detailPage = listPage.enterWorkspace(billingProject, workspaceName)
            detailPage.deleteWorkspace()
            listPage.validateLocation()
            listPage.hasWorkspace(billingProject, workspaceName) shouldBe false
          }
        }

      }

      "and who owns the project" - {
        "should see the Project Cost section of the summary page" in withWebDriver { implicit driver =>
          val user = UserPool.chooseProjectOwner
          implicit val authToken: AuthToken = authTokenOwner
          val testName = "WorkspaceSpec_projectOwnerAccess_projectCost"
          withWorkspace(billingProject, testName, Set.empty, List.empty) { workspaceName =>
            withSignIn(user) { listPage =>
              val workspacePage = listPage.enterWorkspace(billingProject, workspaceName)
              workspacePage.hasGoogleBillingLink shouldBe true
              workspacePage.hasStorageCostEstimate shouldBe true
            }
          }
        }
      }

      "should be able to share the workspace" in withWebDriver { implicit driver =>
        val Seq(user1, user2) = UserPool.chooseStudents(2)
        implicit val authToken: AuthToken = user1.makeAuthToken()
        withWorkspace(billingProject, "WorkspaceSpec_share") { workspaceName =>
          withSignIn(user1) { listPage =>
            val detailPage = listPage.enterWorkspace(billingProject, workspaceName)
            detailPage.share(user2.email, "READER")
          }
          withSignIn(user2) { listPage2 =>
            val detailPage2 = listPage2.enterWorkspace(billingProject, workspaceName)
            detailPage2.readAccessLevel() shouldBe WorkspaceAccessLevel.Reader
          }
        }

      }

      "should be able to set can share permissions for other (non-owner) users" in withWebDriver { implicit driver =>
        val Seq(user1, user2) = UserPool.chooseStudents(2)
        implicit val authToken: AuthToken = user1.makeAuthToken()
        withWorkspace(billingProject, "WorkspaceSpec_share") { workspaceName =>
          withSignIn(user1) { listPage =>
            val detailPage = listPage.enterWorkspace(billingProject, workspaceName)
            detailPage.share(user2.email, "READER", true)
          }
          withSignIn(user2) { listPage2 =>
            val detailPage2 = listPage2.enterWorkspace(billingProject, workspaceName)
            detailPage2.hasShareButton shouldBe true
          }
        }

      }

      "should be able to set can compute permissions for users that are writers" in withWebDriver { implicit driver =>
        val Seq(user1, user2) = UserPool.chooseStudents(2)
        implicit val authToken: AuthToken = user1.makeAuthToken()
        withWorkspace(billingProject, "WorkspaceSpec_share") { workspaceName =>
          withSignIn(user1) { listPage =>
            val detailPage = listPage.enterWorkspace(billingProject, workspaceName)
            val aclEditor = detailPage.openShareDialog(user2.email, "WRITER")
            aclEditor.canComputeBox.isEnabled shouldBe true
            aclEditor.canComputeBox.isChecked shouldBe true
            aclEditor.canComputeBox.ensureUnchecked()
            aclEditor.canComputeBox.isChecked shouldBe false
            aclEditor.cancel()
          }
        }

      }

      "should see can compute permission change for users when role changed from writer to reader" in withWebDriver { implicit driver =>
        val Seq(user1, user2) = UserPool.chooseStudents(2)
        implicit val authToken: AuthToken = user1.makeAuthToken()
        withWorkspace(billingProject, "WorkspaceSpec_canCompute") { workspaceName =>
          withSignIn(user1) { listPage =>
            val detailPage = listPage.enterWorkspace(billingProject, workspaceName)
            val aclEditor = detailPage.openShareDialog(user2.email, "WRITER")
            aclEditor.canComputeBox.isChecked shouldBe true
            aclEditor.updateAccess("READER")
            aclEditor.canComputeBox.isChecked shouldBe false
            aclEditor.canComputeBox.isEnabled shouldBe false
            aclEditor.cancel()
          }
        }
      }

      "should see can compute and can share permission change for users when role changed to no access" in withWebDriver { implicit driver =>
        val Seq(user1, user2) = UserPool.chooseStudents(2)
        implicit val authToken: AuthToken = user1.makeAuthToken()
        withWorkspace(billingProject, "WorkspaceSpec_noAccess") { workspaceName =>
          withSignIn(user1) { listPage =>
            val detailPage = listPage.enterWorkspace(billingProject, workspaceName)
            val aclEditor = detailPage.openShareDialog(user2.email, "WRITER")
            aclEditor.canComputeBox.isChecked shouldBe true
            aclEditor.updateAccess("NO ACCESS")
            aclEditor.canComputeBox.isChecked shouldBe false
            aclEditor.canComputeBox.isEnabled shouldBe false
            aclEditor.canShareBox.isChecked shouldBe false
            aclEditor.canShareBox.isEnabled shouldBe false
            aclEditor.cancel()
          }
        }
      }

      "should not be able to set/change can compute permissions for other owners" in withWebDriver { implicit driver =>
        val Seq(user1, user2) = UserPool.chooseStudents(2)
        implicit val authToken: AuthToken = user1.makeAuthToken()
        withWorkspace(billingProject, "WorkspaceSpec_share") { workspaceName =>
          withSignIn(user1) { listPage =>
            api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)
            api.methodConfigurations.createMethodConfigInWorkspace(billingProject, workspaceName, SimpleMethod, SimpleMethodConfig.configNamespace, s"$methodConfigName Config", 1,
              SimpleMethodConfig.inputs, SimpleMethodConfig.outputs, "participant")
            val detailPage = listPage.enterWorkspace(billingProject, workspaceName)
            val aclEditor = detailPage.openShareDialog(user2.email, "OWNER")
            aclEditor.canComputeBox.isEnabled shouldBe false
            aclEditor.canComputeBox.isChecked shouldBe true
            aclEditor.cancel()
          }
        }

      }

      //reader permissions should always be false
      "should not be able to set/change compute permissions for readers" in withWebDriver { implicit driver =>
        val Seq(user1, user2) = UserPool.chooseStudents(2)
        implicit val authToken: AuthToken = user1.makeAuthToken()
        withWorkspace(billingProject, "WorkspaceSpec_share") { workspaceName =>
          withSignIn(user1) { listPage =>
            api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)
            api.methodConfigurations.createMethodConfigInWorkspace(billingProject, workspaceName, SimpleMethod, SimpleMethodConfig.configNamespace, s"$methodConfigName Config", 1,
              SimpleMethodConfig.inputs, SimpleMethodConfig.outputs, "participant")
            val detailPage = listPage.enterWorkspace(billingProject, workspaceName)
            val aclEditor = detailPage.openShareDialog(user2.email, "READER")
            aclEditor.canComputeBox.isEnabled shouldBe false
            aclEditor.canComputeBox.isChecked shouldBe false
            aclEditor.cancel()
          }
        }

      }

      "should be able to enter workspace attributes" in withWebDriver { implicit driver =>
        val user = UserPool.chooseStudent
        implicit val authToken: AuthToken = user.makeAuthToken()
        withWorkspace(billingProject, "WorkspaceSpec_add_ws_attrs") { workspaceName =>
          withSignIn(user) { listPage =>
            val detailPage = listPage.enterWorkspace(billingProject, workspaceName)

            detailPage.edit {
              detailPage.addWorkspaceAttribute("a", "X")
              detailPage.addWorkspaceAttribute("b", "Y")
              detailPage.addWorkspaceAttribute("c", "Z")
            }

            // TODO: ensure sort, for now it's default sorted by key, ascending
            detailPage.readWorkspaceTable shouldBe List(List("a", "X"), List("b", "Y"), List("c", "Z"))
          }
        }
      }

      // This table is notorious for getting out of sync
      "should be able to correctly delete workspace attributes" - {
        "from the top" in withWebDriver { implicit driver =>
          val user = UserPool.chooseStudent
          implicit val authToken: AuthToken = user.makeAuthToken()
          withWorkspace(billingProject, "WorkspaceSpec_del_ws_attrs", attributes = Some(testAttributes)) { workspaceName =>
            withSignIn(user) { listPage =>
              val detailPage = listPage.enterWorkspace(billingProject, workspaceName)

              detailPage.edit {
                detailPage.deleteWorkspaceAttribute("A-key")
              }

              detailPage.readWorkspaceTable shouldBe List(List("B-key", "B value"), List("C-key", "C value"))
            }
          }
        }

        "from the middle" in withWebDriver { implicit driver =>
          val user = UserPool.chooseStudent
          implicit val authToken: AuthToken = user.makeAuthToken()
          withWorkspace(billingProject, "WorkspaceSpec_del_ws_attrs", attributes = Some(testAttributes)) { workspaceName =>
            withSignIn(user) { listPage =>
              val detailPage = listPage.enterWorkspace(billingProject, workspaceName)

              detailPage.edit {
                detailPage.deleteWorkspaceAttribute("B-key")
              }

              detailPage.readWorkspaceTable shouldBe List(List("A-key", "A value"), List("C-key", "C value"))
            }
          }
        }

        "from the bottom" in withWebDriver { implicit driver =>
          val user = UserPool.chooseStudent
          implicit val authToken: AuthToken = user.makeAuthToken()
          withWorkspace(billingProject, "WorkspaceSpec_del_ws_attrs", attributes = Some(testAttributes)) { workspaceName =>
            withSignIn(user) { listPage =>
              val detailPage = listPage.enterWorkspace(billingProject, workspaceName)

              detailPage.edit {
                detailPage.deleteWorkspaceAttribute("C-key")
              }

              detailPage.readWorkspaceTable shouldBe List(List("A-key", "A value"), List("B-key", "B value"))
            }
          }
        }

        "after adding them" in withWebDriver { implicit driver =>
          val user = UserPool.chooseStudent
          implicit val authToken: AuthToken = user.makeAuthToken()
          withWorkspace(billingProject, "WorkspaceSpec_del_ws_attrs") { workspaceName =>
            withSignIn(user) { listPage =>
              val detailPage = listPage.enterWorkspace(billingProject, workspaceName)

              detailPage.edit {
                detailPage.addWorkspaceAttribute("a", "W")
                detailPage.addWorkspaceAttribute("b", "X")
                detailPage.addWorkspaceAttribute("c", "Y")
              }

              detailPage.readWorkspaceTable shouldBe List(List("a", "W"), List("b", "X"), List("c", "Y"))

              detailPage.edit {
                detailPage.addWorkspaceAttribute("d", "Z")
              }

              detailPage.readWorkspaceTable shouldBe List(List("a", "W"), List("b", "X"), List("c", "Y"), List("d", "Z"))

              detailPage.edit {
                detailPage.deleteWorkspaceAttribute("c")
              }

              detailPage.readWorkspaceTable shouldBe List(List("a", "W"), List("b", "X"), List("d", "Z"))

              detailPage.edit {
                detailPage.deleteWorkspaceAttribute("b")
              }

              detailPage.readWorkspaceTable shouldBe List(List("a", "W"), List("d", "Z"))
            }
          }
        }
      }
    }
  }

}
