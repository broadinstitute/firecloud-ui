package org.broadinstitute.dsde.firecloud.test.security

import org.broadinstitute.dsde.firecloud.api.{AclEntry, WorkspaceAccessLevel}
import org.broadinstitute.dsde.firecloud.config.{AuthToken, AuthTokens, Config}
import org.broadinstitute.dsde.firecloud.fixture.{GroupFixtures, WorkspaceFixtures}
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceSummaryPage
import org.broadinstitute.dsde.firecloud.test.{CleanUp, WebBrowserSpec}
import org.scalatest._

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
class AuthDomainSpec extends FreeSpec /*with ParallelTestExecution*/ with Matchers
  with CleanUp with WebBrowserSpec with WorkspaceFixtures
  with GroupFixtures {

  val projectName: String = Config.Projects.common

  // Unless otherwise declared, this auth token will be used for API calls.
  implicit val authToken: AuthToken = AuthTokens.fred


  "A workspace with an authorization domain" - {

    "can be created by a user who is in a managed group" in withWebDriver { implicit driver =>
      withGroup("AuthDomainSpec") { authDomainName =>
        withCleanUp {
          val workspaceListPage = signIn(Config.Users.fred)

          val workspaceName = "AuthDomainSpec_create_" + randomUuid
          register cleanUp api.workspaces.delete(projectName, workspaceName)
          val workspaceDetailPage = workspaceListPage.createWorkspace(projectName, workspaceName, Option(authDomainName)).awaitLoaded()

          workspaceDetailPage.ui.readAuthDomainRestrictionMessage should include (authDomainName)

          workspaceListPage.open.filter(workspaceName)
          workspaceListPage.ui.looksRestricted(projectName, workspaceName) shouldEqual true
        }
      }
    }

    "when not shared with a user who is not in the authorization domain" - {

      "should not be accessible" in withWebDriver { implicit driver =>
        withGroup("AuthDomainSpec") { authDomainName =>
          withWorkspace(projectName, "AuthDomainSpec", authDomainName) { workspaceName =>
            signIn(Config.Users.ron)

            val workspaceSummaryPage = new WorkspaceSummaryPage(projectName, workspaceName)
            go to workspaceSummaryPage
            workspaceSummaryPage.awaitLoaded()
            workspaceSummaryPage.ui.readError() should include(projectName)
            workspaceSummaryPage.ui.readError() should include(workspaceName)
            workspaceSummaryPage.ui.readError() should include("does not exist")
          }
        }
      }

      "should not be visible" in withWebDriver { implicit driver =>
        withGroup("AuthDomainSpec") { authDomainName =>
          withWorkspace(projectName, "AuthDomainSpec_share", authDomainName) { workspaceName =>
            val listPage = signIn(Config.Users.ron)
            listPage.filter(workspaceName)
            listPage.ui.hasWorkspace(projectName, workspaceName) shouldEqual false
          }
        }
      }
    }

    "when not shared with a user who is in the authorization domain" - {

      "should not be accessible" in withWebDriver { implicit driver =>
        withGroup("AuthDomainSpec", List(Config.Users.george.email)) { authDomainName =>
          withWorkspace(projectName, "AuthDomainSpec", authDomainName) { workspaceName =>
            signIn(Config.Users.george)

            val workspaceSummaryPage = new WorkspaceSummaryPage(projectName, workspaceName)
            go to workspaceSummaryPage
            workspaceSummaryPage.awaitLoaded()
            workspaceSummaryPage.ui.readError() should include(projectName)
            workspaceSummaryPage.ui.readError() should include(workspaceName)
            workspaceSummaryPage.ui.readError() should include("does not exist")
          }
        }
      }

      "should not be visible" in withWebDriver { implicit driver =>
        withGroup("AuthDomainSpec", List(Config.Users.george.email)) { authDomainName =>
          withWorkspace(projectName, "AuthDomainSpec_share", authDomainName) { workspaceName =>
            val listPage = signIn(Config.Users.george)
            listPage.filter(workspaceName)
            listPage.ui.hasWorkspace(projectName, workspaceName) shouldEqual false
          }
        }
      }
    }

    "when shared with a user who is not in the authorization domain" - {

      "should be visible but not accessible" in withWebDriver { implicit driver =>
        withGroup("AuthDomainSpec") { authDomainName =>
          withWorkspace(projectName, "AuthDomainSpec_reject", authDomainName) { workspaceName =>
            api.workspaces.updateAcl(projectName, workspaceName, Config.Users.ron.email, WorkspaceAccessLevel.Reader)

            val workspaceListPage = signIn(Config.Users.ron)
            workspaceListPage.filter(workspaceName)
            workspaceListPage.ui.looksRestricted(projectName, workspaceName) shouldEqual true

            workspaceListPage.ui.clickWorkspaceInList(projectName, workspaceName)
            // micro-sleep just long enough to let the app navigate elsewhere if it's going to, which it shouldn't in this case
            Thread sleep 500
            workspaceListPage.validateLocation()
            // TODO: add assertions for the new "request access" modal
          }
        }
      }
    }

    "when shared with a user who is in the authorization domain" - {

      "should be visible and accessible when shared with single user" in withWebDriver { implicit driver =>
        withGroup("AuthDomainSpec", List(Config.Users.george.email)) { authDomainName =>
          withWorkspace(projectName, "AuthDomainSpec_share", authDomainName, List(AclEntry(Config.Users.george.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
            val listPage = signIn(Config.Users.george)
            listPage.filter(workspaceName)
            listPage.ui.looksRestricted(projectName, workspaceName) shouldEqual true

            val summaryPage = listPage.openWorkspaceDetails(projectName, workspaceName).awaitLoaded()
            summaryPage.ui.readAuthDomainRestrictionMessage should include (authDomainName)
          }
        }
      }

      "should be visible and accessible when shared with a group" in withWebDriver { implicit driver =>
        withGroup("AuthDomainSpec", List(Config.Users.george.email)) { authDomainName =>
          withWorkspace(projectName, "AuthDomainSpec_share", authDomainName, List(AclEntry(s"GROUP_$authDomainName@quality.firecloud.org", WorkspaceAccessLevel.Reader))) { workspaceName =>
            val listPage = signIn(Config.Users.george)
            listPage.filter(workspaceName)
            listPage.ui.looksRestricted(projectName, workspaceName) shouldEqual true

            val summaryPage = listPage.openWorkspaceDetails(projectName, workspaceName).awaitLoaded()
            summaryPage.ui.readAuthDomainRestrictionMessage should include (authDomainName)
          }
        }
      }

      "can be cloned" in withWebDriver { implicit driver =>
        withGroup("AuthDomainSpec", List(Config.Users.george.email)) { authDomainName =>
          withWorkspace(projectName, "AuthDomainSpec_share", authDomainName, List(AclEntry(Config.Users.george.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
            withCleanUp {
              val listPage = signIn(Config.Users.george)
              val summaryPage = listPage.openWorkspaceDetails(projectName, workspaceName)

              val cloneWorkspaceName = workspaceName + "_clone"
              register cleanUp { api.workspaces.delete(projectName, cloneWorkspaceName)(AuthTokens.george) }
              val cloneSummaryPage = summaryPage.cloneWorkspace(projectName, cloneWorkspaceName).awaitLoaded()
              cloneSummaryPage.ui.readWorkspaceName should be(cloneWorkspaceName)
              cloneSummaryPage.ui.readAuthDomainRestrictionMessage should include(authDomainName)
            }
          }
        }
      }
    }

    "cannot lose its authorization domain when cloned" in withWebDriver { implicit driver =>
      withGroup("AuthDomainSpec", List(Config.Users.george.email)) { authDomainName =>
        withWorkspace(projectName, "AuthDomainSpec_share", authDomainName) { workspaceName =>
          withCleanUp {
            api.workspaces.updateAcl(projectName, workspaceName,
              Config.Users.george.email, WorkspaceAccessLevel.Reader)

            val listPage = signIn(Config.Users.george)
            val summaryPage = listPage.openWorkspaceDetails(projectName, workspaceName)

            val cloneWorkspaceName = workspaceName + "_clone"
            val cloneModal = summaryPage.ui.clickCloneButton()
            cloneModal.ui.readPresetAuthDomain() should be(Some(authDomainName))

            register cleanUp { api.workspaces.delete(projectName, cloneWorkspaceName)(AuthTokens.george) }
            cloneModal.cloneWorkspace(projectName, cloneWorkspaceName)
            cloneModal.cloneWorkspaceWait()
            summaryPage.cloneWorkspaceWait()

            val cloneSummaryPage = new WorkspaceSummaryPage(projectName, cloneWorkspaceName).awaitLoaded()
            cloneSummaryPage.ui.readWorkspaceName should be(cloneWorkspaceName)
            cloneSummaryPage.ui.readAuthDomainRestrictionMessage should include(authDomainName)
          }
        }
      }
    }
  }
}
