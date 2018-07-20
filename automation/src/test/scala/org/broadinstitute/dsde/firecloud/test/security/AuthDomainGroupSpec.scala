package org.broadinstitute.dsde.firecloud.test.security

import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.firecloud.page.workspaces.summary.WorkspaceSummaryPage
import org.broadinstitute.dsde.firecloud.test.Tags
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{Credentials, UserPool}
import org.broadinstitute.dsde.workbench.fixture.{BillingFixtures, GroupFixtures, TestReporterFixture, WorkspaceFixtures}
import org.broadinstitute.dsde.workbench.service.{AclEntry, Orchestration, WorkspaceAccessLevel}
import org.broadinstitute.dsde.workbench.service.Orchestration.billing.BillingProjectRole
import org.broadinstitute.dsde.workbench.service.test.{CleanUp, WebBrowserSpec}
import org.scalatest._
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Seconds, Span}


class AuthDomainGroupSpec extends FreeSpec with ParallelTestExecution with Matchers with CleanUp
  with WebBrowserSpec with WorkspaceFixtures with Eventually with BillingFixtures with GroupFixtures with UserFixtures
  with TestReporterFixture {

  implicit override val patienceConfig = PatienceConfig(timeout = scaled(Span(10, Seconds)), interval = scaled(Span(500, Millis)))

  /*
   * Unless otherwise declared, this auth token will be used for API calls.
   * We are using a curator to prevent collisions with users in tests (who are Students and AuthDomainUsers), not
   *  because we specifically need a curator.
   */
  val defaultUser: Credentials = UserPool.chooseCurator
  val authTokenDefault: AuthToken = defaultUser.makeAuthToken()

  private def checkWorkspaceFailure(workspaceSummaryPage: WorkspaceSummaryPage, projectName: String, workspaceName: String): Unit = {
    val error = workspaceSummaryPage.readError()
    error should include(projectName)
    error should include(workspaceName)
    error should include("does not exist")
  }

  // ONE-GROUP AUTH DOMAIN TESTS

  "A workspace with an authorization domain" - {
    "with one group inside of it" - {

      "can be created" in {
        val user = UserPool.chooseAuthDomainUser
        implicit val authToken: AuthToken = authTokenDefault
        withGroup("AuthDomain", List(user.email)) { authDomainName =>
          withCleanUp {
            withCleanBillingProject(user) { projectName =>
              withWebDriver { implicit driver =>
                withSignIn(user) { listPage =>
                  val workspaceName = "AuthDomainSpec_create_" + randomUuid
                  register cleanUp api.workspaces.delete(projectName, workspaceName)(user.makeAuthToken())
                  val workspaceSummaryPage = listPage.createWorkspace(projectName, workspaceName, Set(authDomainName))

                  eventually {
                    workspaceSummaryPage.readAuthDomainGroups should include(authDomainName)
                  }
                }
              }
            }
          }
        }
      }

      "can be cloned and retain the auth domain" taggedAs Tags.SmokeTest in {
        val user = UserPool.chooseAuthDomainUser
        implicit val authToken: AuthToken = authTokenDefault
        withGroup("AuthDomain", List(user.email)) { authDomainName =>
          withCleanBillingProject(defaultUser) { projectName =>
            Orchestration.billing.addUserToBillingProject(projectName, user.email, BillingProjectRole.User)
            withWorkspace(projectName, "AuthDomainSpec_share", Set(authDomainName), List(AclEntry(user.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
              withCleanUp {
                withWebDriver { implicit driver =>
                  withSignIn(user) { listPage =>
                    val summaryPage = listPage.enterWorkspace(projectName, workspaceName)

                    val cloneWorkspaceName = workspaceName + "_clone"
                    val cloneModal = summaryPage.clickCloneButton()
                    eventually {
                      cloneModal.readLockedAuthDomainGroups() should contain(authDomainName)
                    }

                    register cleanUp api.workspaces.delete(projectName, cloneWorkspaceName)(authTokenDefault)

                    val cloneSummaryPage = cloneModal.cloneWorkspace(projectName, cloneWorkspaceName)
                    eventually {
                      cloneSummaryPage.validateWorkspace shouldEqual true
                    }
                    eventually {
                      cloneSummaryPage.readAuthDomainGroups should include(authDomainName)
                    }
                  }
                }
              }
            }
          }
        }
      }

      "when the user is not inside of the group" - {
        "when the workspace is shared with them" - {

          "can be seen but is not accessible" in {
            val user = UserPool.chooseStudent
            implicit val authToken: AuthToken = authTokenDefault
            withGroup("AuthDomain") { authDomainName =>
              withCleanBillingProject(defaultUser) { projectName =>
                withWorkspace(projectName, "AuthDomainSpec_reject", Set(authDomainName), List(AclEntry(user.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
                  withWebDriver { implicit driver =>
                    withSignIn(user) { workspaceListPage =>
                      workspaceListPage.clickWorkspaceLink(projectName, workspaceName)
                      eventually { workspaceListPage.showsRequestAccessModal shouldEqual true }
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
          }
        }
        "when the workspace is not shared with them" - {
          "cannot be seen and is not accessible" in {
            val user = UserPool.chooseStudent
            implicit val authToken: AuthToken = authTokenDefault
            withGroup("AuthDomain") { authDomainName =>
              withCleanBillingProject(defaultUser) { projectName =>
                withWorkspace(projectName, "AuthDomainSpec", Set(authDomainName)) { workspaceName =>
                  withWebDriver { implicit driver =>
                    withSignIn(user) { workspaceListPage =>
                      eventually {
                        workspaceListPage.hasWorkspace(projectName, workspaceName) shouldEqual false
                      }

                      val workspaceSummaryPage = new WorkspaceSummaryPage(projectName, workspaceName).open
                      checkWorkspaceFailure(workspaceSummaryPage, projectName, workspaceName)
                    }
                  }
                }
              }
            }
          }
        }
      }

      "when the user is inside of the group" - {
        "when the workspace is shared with them" - {

          "can be seen and is accessible" in {
            val user = UserPool.chooseAuthDomainUser
            implicit val authToken: AuthToken = authTokenDefault
            withGroup("AuthDomain", List(user.email)) { authDomainName =>
              withCleanBillingProject(defaultUser) { projectName =>
                withWorkspace(projectName, "AuthDomainSpec_share", Set(authDomainName), List(AclEntry(user.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
                  withWebDriver { implicit driver =>
                    withSignIn(user) { listPage =>
                      val summaryPage = listPage.enterWorkspace(projectName, workspaceName)
                      eventually {
                        summaryPage.readAuthDomainGroups should include(authDomainName)
                      }
                    }
                  }
                }
              }
            }
          }
        }
        "when the workspace is not shared with them" - {
          "cannot be seen and is not accessible" in {
            val user = UserPool.chooseAuthDomainUser
            implicit val authToken: AuthToken = authTokenDefault
            withGroup("AuthDomain", List(user.email)) { authDomainName =>
              withCleanBillingProject(defaultUser) { projectName =>
                withWorkspace(projectName, "AuthDomainSpec", Set(authDomainName)) { workspaceName =>
                  withWebDriver { implicit driver =>
                    withSignIn(user) { workspaceListPage =>
                      eventually {
                        workspaceListPage.hasWorkspace(projectName, workspaceName) shouldEqual false
                      }

                      val workspaceSummaryPage = new WorkspaceSummaryPage(projectName, workspaceName).open
                      checkWorkspaceFailure(workspaceSummaryPage, projectName, workspaceName)
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

}
