package org.broadinstitute.dsde.firecloud.test.workspace

import java.util.UUID

import org.broadinstitute.dsde.firecloud.fixture.{TestData, UserFixtures}
import org.broadinstitute.dsde.firecloud.page.workspaces.summary.WorkspaceSummaryPage
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{Credentials, UserPool}
import org.broadinstitute.dsde.workbench.fixture._
import org.broadinstitute.dsde.workbench.service.test.{RandomUtil, WebBrowserSpec}
import org.broadinstitute.dsde.workbench.service.{RestException, WorkspaceAccessLevel}
import org.scalatest._
import org.scalatest.time.{Millis, Seconds, Span}

import scala.util.Try

class WorkspaceOwnerSpec extends FreeSpec with Matchers with WebBrowserSpec
  with RandomUtil with WorkspaceFixtures with UserFixtures with MethodFixtures with BillingFixtures with TestReporterFixture {

  override implicit val patienceConfig = PatienceConfig(timeout = scaled(Span(10, Seconds)), interval = scaled(Span(500, Millis)))

  val projectOwner: Credentials = UserPool.chooseProjectOwner
  val authTokenOwner: AuthToken = projectOwner.makeAuthToken()
  val methodConfigName: String = SimpleMethodConfig
    .configName + "_" + UUID.randomUUID().toString + "Config"

  val testAttributes = Map("A-key" -> "A value", "B-key" -> "B value", "C-key" -> "C value")
  val noAccessText = "You do not have access to run analysis."


  "A user" - {
    "with a billing project" - {
      "should be able to create a workspace" in {
        val user = UserPool.chooseStudent
        implicit val authToken: AuthToken = user.makeAuthToken()
        withCleanBillingProject(user) { billingProject =>
          withWebDriver { implicit driver =>
            withSignIn(user) { listPage =>
              val workspaceName = "WorkspaceSpec_create_" + randomUuid
              register cleanUp api.workspaces.delete(billingProject, workspaceName)
              val detailPage = listPage.createWorkspace(billingProject, workspaceName)

              eventually { detailPage.validateWorkspace shouldEqual true }

              listPage.open
              eventually { listPage.hasWorkspace(billingProject, workspaceName) shouldBe true }
            }
          }
        }
      }

      "should be able to clone a workspace" in {
        val user = UserPool.chooseStudent
        implicit val authToken: AuthToken = user.makeAuthToken()
        withCleanBillingProject(user) { billingProject =>
          withWorkspace(billingProject, "WorkspaceSpec_to_be_cloned") { workspaceName =>
            withWebDriver { implicit driver =>
              withSignIn(user) { listPage =>
                val workspaceNameCloned = "WorkspaceSpec_clone_" + randomUuid
                val workspaceSummaryPage = new WorkspaceSummaryPage(billingProject, workspaceName).open
                register cleanUp api.workspaces.delete(billingProject, workspaceNameCloned)
                workspaceSummaryPage.cloneWorkspace(billingProject, workspaceNameCloned)

                listPage.open
                eventually { listPage.hasWorkspace(billingProject, workspaceNameCloned) shouldBe true }
              }
            }
          }
        }
      }
    }

    "who owns a workspace" - {
      "should be able to delete the workspace" in {
        val user = UserPool.chooseStudent
        implicit val authToken: AuthToken = user.makeAuthToken()
        withCleanBillingProject(user) { billingProject =>
          withWorkspace(billingProject, "WorkspaceOwnerSpec_delete", cleanUp = false) { workspaceName =>
            // special cleanup because it is expected to fail as it should already be deleted
            register cleanUp Try(api.workspaces.delete(billingProject, workspaceName)).recover {
              case _: RestException =>
            }
            withWebDriver { implicit driver =>
              withSignIn(user) { listPage =>
                val detailPage = listPage.enterWorkspace(billingProject, workspaceName)
                detailPage.deleteWorkspace()
                listPage.validateLocation()
                eventually { listPage.hasWorkspace(billingProject, workspaceName) shouldBe false }
              }
            }
          }
        }
      }

      "and who owns the project" - {
        "should see the Project Cost section of the summary page" in {
          implicit val authToken: AuthToken = authTokenOwner
          val testName = "WorkspaceSpec_projectOwnerAccess_projectCost"
          withCleanBillingProject(projectOwner) { billingProject =>
            withWorkspace(billingProject, testName, Set.empty, List.empty) { workspaceName =>
              withWebDriver { implicit driver =>
                withSignIn(projectOwner) { listPage =>
                  val workspacePage = listPage.enterWorkspace(billingProject, workspaceName)
                  eventually { workspacePage.hasGoogleBillingLink shouldBe true }
                  eventually { workspacePage.hasStorageCostEstimate shouldBe true }
                }
              }
            }
          }
        }
      }

      "should be able to share the workspace" in {
        val Seq(user1, user2) = UserPool.chooseStudents(2)
        implicit val authToken: AuthToken = user1.makeAuthToken()
        withCleanBillingProject(user1) { billingProject =>
          withWorkspace(billingProject, "WorkspaceSpec_shareWorkspace") { workspaceName =>
            withWebDriver { implicit driver =>
              withSignIn(user1) { listPage =>
                val detailPage = listPage.enterWorkspace(billingProject, workspaceName)
                detailPage.share(user2.email, "READER")
              }
              withSignIn(user2) { listPage2 =>
                val detailPage2 = listPage2.enterWorkspace(billingProject, workspaceName)
               eventually { detailPage2.readAccessLevel() shouldBe WorkspaceAccessLevel.Reader }
              }
            }
          }
        }
      }

      "should be able to set can share permissions for other (non-owner) users" in {
        val Seq(user1, user2) = UserPool.chooseStudents(2)
        implicit val authToken: AuthToken = user1.makeAuthToken()
        withCleanBillingProject(user1) { billingProject =>
          withWorkspace(billingProject, "WorkspaceOwnerSpec_sharePermission") { workspaceName =>
            withWebDriver { implicit driver =>
              withSignIn(user1) { listPage =>
                val detailPage = listPage.enterWorkspace(billingProject, workspaceName)
                detailPage.share(user2.email, "READER", share = true)
              }
              withSignIn(user2) { listPage2 =>
                val detailPage2 = listPage2.enterWorkspace(billingProject, workspaceName)
                eventually { detailPage2.hasShareButton shouldBe true }
              }
            }
          }
        }
      }

      "should be able to set can compute permissions for users that are writers" in {
        val Seq(user1, user2) = UserPool.chooseStudents(2)
        implicit val authToken: AuthToken = user1.makeAuthToken()
        withCleanBillingProject(user1) { billingProject =>
          withWorkspace(billingProject, "WorkspaceOwnerSpec_shareCompute") { workspaceName =>
            withWebDriver { implicit driver =>
              withSignIn(user1) { listPage =>
                val detailPage = listPage.enterWorkspace(billingProject, workspaceName)
                val aclEditor = detailPage.openShareDialog(user2.email, "WRITER")
                eventually { aclEditor.canComputeBox.isEnabled shouldBe true }
                eventually { aclEditor.canComputeBox.isChecked shouldBe true }
                eventually { aclEditor.canComputeBox.ensureUnchecked() }
                eventually { aclEditor.canComputeBox.isChecked shouldBe false }
                aclEditor.cancel()
              }
            }
          }
        }
      }

      "should see can compute permission change for users when role changed from writer to reader" in {
        val Seq(user1, user2) = UserPool.chooseStudents(2)
        implicit val authToken: AuthToken = user1.makeAuthToken()
        withCleanBillingProject(user1) { billingProject =>
          withWorkspace(billingProject, "WorkspaceOwnerSpec_canCompute") { workspaceName =>
            withWebDriver { implicit driver =>
              withSignIn(user1) { listPage =>
                val detailPage = listPage.enterWorkspace(billingProject, workspaceName)
                val aclEditor = detailPage.openShareDialog(user2.email, "WRITER")
                eventually { aclEditor.canComputeBox.isChecked shouldBe true }
                aclEditor.updateAccess("READER")
                eventually { aclEditor.canComputeBox.isChecked shouldBe false }
                  eventually { aclEditor.canComputeBox.isEnabled shouldBe false }
                aclEditor.cancel()
              }
            }
          }
        }
      }

      "should see can compute and can share permission change for users when role changed to no access" in {
        val Seq(user1, user2) = UserPool.chooseStudents(2)
        implicit val authToken: AuthToken = user1.makeAuthToken()
        withCleanBillingProject(user1) { billingProject =>
          withWorkspace(billingProject, "WorkspaceOwnerSpec_noAccess") { workspaceName =>
            withWebDriver { implicit driver =>
              withSignIn(user1) { listPage =>
                val detailPage = listPage.enterWorkspace(billingProject, workspaceName)
                val aclEditor = detailPage.openShareDialog(user2.email, "WRITER")
                eventually { aclEditor.canComputeBox.isChecked shouldBe true }
                aclEditor.updateAccess("NO ACCESS")
                eventually { aclEditor.canComputeBox.isChecked shouldBe false }
                eventually { aclEditor.canComputeBox.isEnabled shouldBe false }
                eventually { aclEditor.canShareBox.isChecked shouldBe false }
                eventually { aclEditor.canShareBox.isEnabled shouldBe false }
                aclEditor.cancel()
              }
            }
          }
        }
      }

      "should not be able to set/change can compute permissions for other owners" in {
        val Seq(user1, user2) = UserPool.chooseStudents(2)
        implicit val authToken: AuthToken = user1.makeAuthToken()
        withCleanBillingProject(user1) { billingProject =>
          withWorkspace(billingProject, "WorkspaceOwnerSpec_noComputeOwner") { workspaceName =>
            withWebDriver { implicit driver =>
              withSignIn(user1) { listPage =>
                api.importMetaData(billingProject, workspaceName, "entities", TestData().participantEntity)
                val detailPage = listPage.enterWorkspace(billingProject, workspaceName)
                val aclEditor = detailPage.openShareDialog(user2.email, "OWNER")
                eventually { aclEditor.canComputeBox.isEnabled shouldBe false }
                  eventually { aclEditor.canComputeBox.isChecked shouldBe true }
                aclEditor.cancel()
              }
            }
          }
        }

      }
      //reader permissions should always be false
      "should not be able to set/change compute permissions for readers" in {
        val Seq(user1, user2) = UserPool.chooseStudents(2)
        implicit val authToken: AuthToken = user1.makeAuthToken()
        withCleanBillingProject(user1) { billingProject =>
          withWorkspace(billingProject, "WorkspaceOwnerSpec_noComputerReader") { workspaceName =>
            withWebDriver { implicit driver =>
              withSignIn(user1) { listPage =>
                api.importMetaData(billingProject, workspaceName, "entities", TestData().participantEntity)
                val detailPage = listPage.enterWorkspace(billingProject, workspaceName)
                val aclEditor = detailPage.openShareDialog(user2.email, "READER")
                eventually { aclEditor.canComputeBox.isEnabled shouldBe false }
                  eventually { aclEditor.canComputeBox.isChecked shouldBe false }
                aclEditor.cancel()
              }
            }
          }
        }

      }

      "should be able to enter workspace attributes" in {
        val user = UserPool.chooseStudent
        implicit val authToken: AuthToken = user.makeAuthToken()
        withCleanBillingProject(user) { billingProject =>
          withWorkspace(billingProject, "WorkspaceOwnerSpec_add_attrs") { workspaceName =>
            withWebDriver { implicit driver =>
              withSignIn(user) { listPage =>
                val detailPage = listPage.enterWorkspace(billingProject, workspaceName)

                detailPage.edit {
                  detailPage.addWorkspaceAttribute("a", "X")
                  detailPage.addWorkspaceAttribute("b", "Y")
                  detailPage.addWorkspaceAttribute("c", "Z")
                }

                // TODO: ensure sort, for now it's default sorted by key, ascending
                eventually { detailPage.readWorkspaceTable shouldBe List(List("a", "X"), List("b", "Y"), List("c", "Z")) }
              }
            }
          }
        }

      }

      // This table is notorious for getting out of sync
      "should be able to correctly delete workspace attributes" - {
        "from the top" in {
          val user = UserPool.chooseStudent
          implicit val authToken: AuthToken = user.makeAuthToken()
          withCleanBillingProject(user) { billingProject =>
            withWorkspace(billingProject, "WorkspaceOwnerSpec_del_attrs_fromTop", attributes = Some(testAttributes)) { workspaceName =>
              withWebDriver { implicit driver =>
                withSignIn(user) { listPage =>
                  val detailPage = listPage.enterWorkspace(billingProject, workspaceName)

                  detailPage.edit {
                    detailPage.deleteWorkspaceAttribute("A-key")
                  }

                  eventually { detailPage.readWorkspaceTable shouldBe List(List("B-key", "B value"), List("C-key", "C value")) }
                }
              }
            }
          }

        }

        "from the middle" in {
          val user = UserPool.chooseStudent
          implicit val authToken: AuthToken = user.makeAuthToken()
          withCleanBillingProject(user) { billingProject =>
            withWorkspace(billingProject, "WorkspaceOwnerSpec_del_attrs_fromMiddle", attributes = Some(testAttributes)) { workspaceName =>
              withWebDriver { implicit driver =>
                withSignIn(user) { listPage =>
                  val detailPage = listPage.enterWorkspace(billingProject, workspaceName)

                  detailPage.edit {
                    detailPage.deleteWorkspaceAttribute("B-key")
                  }

                  eventually { detailPage.readWorkspaceTable shouldBe List(List("A-key", "A value"), List("C-key", "C value")) }
                }
              }
            }
          }

        }

        "from the bottom" in {
          val user = UserPool.chooseStudent
          implicit val authToken: AuthToken = user.makeAuthToken()
          withCleanBillingProject(user) { billingProject =>
            withWorkspace(billingProject, "WorkspaceOwnerSpec_del_attrs_fromBottom", attributes = Some(testAttributes)) { workspaceName =>
              withWebDriver { implicit driver =>
                withSignIn(user) { listPage =>
                  val detailPage = listPage.enterWorkspace(billingProject, workspaceName)

                  detailPage.edit {
                    detailPage.deleteWorkspaceAttribute("C-key")
                  }

                  eventually { detailPage.readWorkspaceTable shouldBe List(List("A-key", "A value"), List("B-key", "B value")) }
                }
              }
            }
          }

        }

        "after adding them" in {
          val user = UserPool.chooseStudent
          implicit val authToken: AuthToken = user.makeAuthToken()
          withCleanBillingProject(user) { billingProject =>
            withWorkspace(billingProject, "WorkspaceOwnerSpec_del_attrs_afterAdd") { workspaceName =>
              withWebDriver { implicit driver =>
                withSignIn(user) { listPage =>
                  val detailPage = listPage.enterWorkspace(billingProject, workspaceName)

                  detailPage.edit {
                    detailPage.addWorkspaceAttribute("a", "W")
                    detailPage.addWorkspaceAttribute("b", "X")
                    detailPage.addWorkspaceAttribute("c", "Y")
                  }

                  eventually { detailPage.readWorkspaceTable shouldBe List(List("a", "W"), List("b", "X"), List("c", "Y")) }

                  detailPage.edit {
                    detailPage.addWorkspaceAttribute("d", "Z")
                  }

                  eventually { detailPage.readWorkspaceTable shouldBe List(List("a", "W"), List("b", "X"), List("c", "Y"), List("d", "Z")) }

                  detailPage.edit {
                    detailPage.deleteWorkspaceAttribute("c")
                  }

                  eventually { detailPage.readWorkspaceTable shouldBe List(List("a", "W"), List("b", "X"), List("d", "Z")) }

                  detailPage.edit {
                    detailPage.deleteWorkspaceAttribute("b")
                  }

                  eventually { detailPage.readWorkspaceTable shouldBe List(List("a", "W"), List("d", "Z")) }
                }
              }
            }
          }

        }
      }
    }
  }
}
