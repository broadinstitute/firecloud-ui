package org.broadinstitute.dsde.firecloud.test.security

import org.broadinstitute.dsde.firecloud.page.workspaces.summary.WorkspaceSummaryPage
import org.broadinstitute.dsde.firecloud.test.Tags
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.UserPool
import org.broadinstitute.dsde.workbench.service.{AclEntry, WorkspaceAccessLevel}

/*
 * This test SHOULD be able to run with ParallelTestExecution. However, Rawls
 * currently has database deadlock problems when creating and deleting managed
 * groups because they involve 3 operations across 2 tables. My initial
 * attempt to retry the deadlock failed because Rawls also creates the groups
 * in Google inside the transaction; attempts to retry the transaction result
 * in 409 Conflict errors from Google.
 *
 * TODO: Fix Rawls group creation/deletion and run these tests in parallel
 */
class AuthDomainSpec extends AuthDomainSpecBase {

  "A workspace with an authorization domain" - {
    "with one group inside of it" - {
      "can be created" in withWebDriver { implicit driver =>
        val user = UserPool.chooseAuthDomainUser
        implicit val authToken: AuthToken = authTokenDefault
        withGroup("AuthDomain", List(user.email)) { authDomainName =>
          withCleanUp {

            withSignIn(user) { listPage =>
              val workspaceName = "AuthDomainSpec_create_" + randomUuid
              register cleanUp api.workspaces.delete(projectName, workspaceName)(user.makeAuthToken())
              val workspaceSummaryPage = listPage.createWorkspace(projectName, workspaceName, Set(authDomainName))

              workspaceSummaryPage.readAuthDomainGroups should include(authDomainName)
            }
          }
        }
      }

      "can be cloned and retain the auth domain" taggedAs Tags.SmokeTest in withWebDriver { implicit driver =>
        val user = UserPool.chooseAuthDomainUser
        implicit val authToken: AuthToken = authTokenDefault
        withGroup("AuthDomain", List(user.email)) { authDomainName =>
          withWorkspace(projectName, "AuthDomainSpec_share", Set(authDomainName), List(AclEntry(user.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
            withCleanUp {
              withSignIn(user) { listPage =>
                val summaryPage = listPage.enterWorkspace(projectName, workspaceName)

                val cloneWorkspaceName = workspaceName + "_clone"
                val cloneModal = summaryPage.clickCloneButton()
                cloneModal.readLockedAuthDomainGroups() should contain(authDomainName)

                register cleanUp api.workspaces.delete(projectName, cloneWorkspaceName)(user.makeAuthToken())

                val cloneSummaryPage = cloneModal.cloneWorkspace(projectName, cloneWorkspaceName)
                cloneSummaryPage.validateWorkspace shouldEqual true
                cloneSummaryPage.readAuthDomainGroups should include(authDomainName)
              }
            }
          }
        }
      }

      "when the user is not inside of the group" - {
        "when the workspace is shared with them" - {
          "can be seen but is not accessible" in withWebDriver { implicit driver =>
            val user = UserPool.chooseStudent
            implicit val authToken: AuthToken = authTokenDefault
            withGroup("AuthDomain") { authDomainName =>
              withWorkspace(projectName, "AuthDomainSpec_reject", Set(authDomainName), List(AclEntry(user.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
                withSignIn(user) { workspaceListPage =>
                  workspaceListPage.clickWorkspaceLink(projectName, workspaceName)
                  workspaceListPage.showsRequestAccessModal shouldEqual true
                  workspaceListPage.validateLocation()
                  // TODO: add assertions for the new "request access" modal
                  // TODO: end test somewhere we can access the sign out button

                  // close "request access" Modal
                  workspaceListPage.closeModal()
                }
              }
            }
          }
        }
        "when the workspace is not shared with them" - {
          "cannot be seen and is not accessible" in withWebDriver { implicit driver =>
            val user = UserPool.chooseStudent
            implicit val authToken: AuthToken = authTokenDefault
            withGroup("AuthDomain") { authDomainName =>
              withWorkspace(projectName, "AuthDomainSpec", Set(authDomainName)) { workspaceName =>
                withSignIn(user) { workspaceListPage =>
                  workspaceListPage.hasWorkspace(projectName, workspaceName) shouldEqual false

                  val workspaceSummaryPage = new WorkspaceSummaryPage(projectName, workspaceName).open
                  checkWorkspaceFailure(workspaceSummaryPage, workspaceName)
                }
              }
            }
          }
        }
      }

      "when the user is inside of the group" - {
        "when the workspace is shared with them" - {
          "can be seen and is accessible" in withWebDriver { implicit driver =>

            val user = UserPool.chooseAuthDomainUser
            implicit val authToken: AuthToken = authTokenDefault
            withGroup("AuthDomain", List(user.email)) { authDomainName =>
              withWorkspace(projectName, "AuthDomainSpec_share", Set(authDomainName), List(AclEntry(user.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
                withSignIn(user) { listPage =>
                  val summaryPage = listPage.enterWorkspace(projectName, workspaceName)
                  summaryPage.readAuthDomainGroups should include(authDomainName)
                }
              }
            }
          }
        }
        "when the workspace is not shared with them" - {
          "cannot be seen and is not accessible" in withWebDriver { implicit driver =>
            val user = UserPool.chooseAuthDomainUser
            implicit val authToken: AuthToken = authTokenDefault
            withGroup("AuthDomain", List(user.email)) { authDomainName =>
              withWorkspace(projectName, "AuthDomainSpec", Set(authDomainName)) { workspaceName =>
                withSignIn(user) { workspaceListPage =>
                  workspaceListPage.hasWorkspace(projectName, workspaceName) shouldEqual false

                  val workspaceSummaryPage = new WorkspaceSummaryPage(projectName, workspaceName).open
                  checkWorkspaceFailure(workspaceSummaryPage, workspaceName)
                }
              }
            }
          }
        }
        //TCGA controlled access workspaces use-case
        "when the workspace is shared with the group" - {
          "can be seen and is accessible" in withWebDriver { implicit driver =>
            val user = UserPool.chooseAuthDomainUser
            implicit val authToken: AuthToken = authTokenDefault
            withGroup("AuthDomain", List(user.email)) { groupOneName =>
              withGroup("AuthDomain", List(user.email)) { groupTwoName =>
                withWorkspace(projectName, "AuthDomainSpec_share", Set(groupOneName, groupTwoName), List(AclEntry(groupNameToEmail(groupOneName), WorkspaceAccessLevel.Reader))) { workspaceName =>
                  withSignIn(user) { listPage =>
                    val summaryPage = listPage.enterWorkspace(projectName, workspaceName)
                    summaryPage.readAuthDomainGroups should include(groupOneName)
                    summaryPage.readAuthDomainGroups should include(groupTwoName)
                  }
                }
              }
            }
          }
        }
      }
    }

  }

}
