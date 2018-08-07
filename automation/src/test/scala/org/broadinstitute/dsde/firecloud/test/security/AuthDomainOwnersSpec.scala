package org.broadinstitute.dsde.firecloud.test.security

import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.firecloud.page.workspaces.RequestAccessModal
import org.broadinstitute.dsde.firecloud.page.workspaces.summary.WorkspaceSummaryPage
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{Credentials, UserPool}
import org.broadinstitute.dsde.workbench.fixture.{BillingFixtures, GroupFixtures, TestReporterFixture, WorkspaceFixtures}
import org.broadinstitute.dsde.workbench.service.RestException
import org.broadinstitute.dsde.workbench.service.Orchestration.billing.BillingProjectRole
import org.broadinstitute.dsde.workbench.service.Orchestration.groups.GroupRole
import org.broadinstitute.dsde.workbench.service.test.{CleanUp, WebBrowserSpec}
import org.scalatest._
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Seconds, Span}

import scala.util.Try

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
class AuthDomainOwnersSpec extends FreeSpec with ParallelTestExecution with Matchers
  with CleanUp with WebBrowserSpec with WorkspaceFixtures with Eventually
  with BillingFixtures with GroupFixtures with UserFixtures with TestReporterFixture {

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

  "removing permissions from billing project owners for workspaces with auth domains" - {
    "+ project owner, + group member, create workspace, - group member" in {
      val owner = UserPool.chooseProjectOwner
      val creator = UserPool.chooseStudent
      val user = owner

      implicit val token: AuthToken = creator.makeAuthToken()

      withCleanBillingProject(owner, List(creator.email)) { projectName =>
        withGroup("AuthDomain", List(user.email)) { groupName =>
          withWorkspace(projectName, "AuthDomainSpec_revoke", Set(groupName)) { workspaceName =>
            checkVisibleAndAccessible(user, projectName, workspaceName)

            api.groups.removeUserFromGroup(groupName, user.email, GroupRole.Member)
            checkVisibleNotAccessible(user, projectName, workspaceName)
          }
        }
      }
    }

    "+ project owner, + group member, create workspace, - project owner" in {
      val owner = UserPool.chooseProjectOwner
      val creator = owner
      val user = UserPool.chooseStudent

      implicit val token: AuthToken = creator.makeAuthToken()

      withCleanBillingProject(creator, ownerEmails = List(user.email)) { projectName =>
        withGroup("AuthDomain", List(user.email)) { groupName =>
          withWorkspace(projectName, "AuthDomainSpec_revoke", Set(groupName)) { workspaceName =>
            checkVisibleAndAccessible(user, projectName, workspaceName)

            api.billing.removeUserFromBillingProject(projectName, user.email, BillingProjectRole.Owner)
            checkNoAccess(user, projectName, workspaceName)
          }
        }
      }
    }

    "+ project owner, create workspace, + group member, - group member" in {
      val owner = UserPool.chooseProjectOwner
      val creator = UserPool.chooseStudent
      val user = owner

      implicit val token: AuthToken = creator.makeAuthToken()

      withCleanBillingProject(owner, List(creator.email)) { projectName =>
        withGroup("AuthDomain") { groupName =>
          withCleanUp {
            withWorkspace(projectName, "AuthDomainSpec_revoke", Set(groupName)) { workspaceName =>

              checkVisibleNotAccessible(user, projectName, workspaceName)

              api.groups.addUserToGroup(groupName, user.email, GroupRole.Member)
              register cleanUp Try(api.groups.removeUserFromGroup(groupName, user.email, GroupRole.Member)).recover {
                case _: RestException =>
              }
              checkVisibleAndAccessible(user, projectName, workspaceName)

              api.groups.removeUserFromGroup(groupName, user.email, GroupRole.Member)
              checkVisibleNotAccessible(user, projectName, workspaceName)
            }
          }
        }
      }
    }

    "+ project owner, create workspace, + group member, - project owner" in {
      val owner = UserPool.chooseProjectOwner
      val creator = owner
      val user = UserPool.chooseStudent

      implicit val token: AuthToken = creator.makeAuthToken()

      withCleanBillingProject(creator, ownerEmails = List(user.email)) { projectName =>
        withGroup("AuthDomain") { groupName =>
          withWorkspace(projectName, "AuthDomainSpec_revoke", Set(groupName)) { workspaceName =>
            checkVisibleNotAccessible(user, projectName, workspaceName)

            api.groups.addUserToGroup(groupName, user.email, GroupRole.Member)
            checkVisibleAndAccessible(user, projectName, workspaceName)

            api.billing.removeUserFromBillingProject(projectName, user.email, BillingProjectRole.Owner)
            checkNoAccess(user, projectName, workspaceName)
          }
        }
      }
    }

    "+ group member, create workspace, + project owner, - group member" in {
      val owner = UserPool.chooseProjectOwner
      val creator = owner
      val user = UserPool.chooseStudent

      implicit val token: AuthToken = creator.makeAuthToken()

      withCleanBillingProject(creator) { projectName =>
        withGroup("AuthDomain", List(user.email)) { groupName =>
          withCleanUp {
            withWorkspace(projectName, "AuthDomainSpec_revoke1x", Set(groupName)) { workspaceName =>
              checkNoAccess(user, projectName, workspaceName)

              api.billing.addUserToBillingProject(projectName, user.email, BillingProjectRole.Owner)
              register cleanUp api.billing.removeUserFromBillingProject(projectName, user.email, BillingProjectRole.Owner)
              checkVisibleAndAccessible(user, projectName, workspaceName)

              api.groups.removeUserFromGroup(groupName, user.email, GroupRole.Member)
              checkVisibleNotAccessible(user, projectName, workspaceName)
            }
          }
        }
      }
    }

    "+ group member, create workspace, + project owner, - project owner" in {
      val owner = UserPool.chooseProjectOwner
      val creator = owner
      val user = UserPool.chooseStudent

      implicit val token: AuthToken = creator.makeAuthToken()

      withCleanBillingProject(creator) { projectName =>
        withGroup("AuthDomain", List(user.email)) { groupName =>
          withCleanUp {
            withWorkspace(projectName, "AuthDomainSpec_revoke", Set(groupName)) { workspaceName =>
              checkNoAccess(user, projectName, workspaceName)

              api.billing.addUserToBillingProject(projectName, user.email, BillingProjectRole.Owner)
              register cleanUp Try(api.billing.removeUserFromBillingProject(projectName, user.email, BillingProjectRole.Owner)).recover {
                case _: RestException =>
              }
              checkVisibleAndAccessible(user, projectName, workspaceName)

              api.billing.removeUserFromBillingProject(projectName, user.email, BillingProjectRole.Owner)
              checkNoAccess(user, projectName, workspaceName)
            }
          }
        }
      }
    }

    "create workspace, + project owner, + group member, - group member" in {
      val owner = UserPool.chooseProjectOwner
      val creator = owner
      val user = UserPool.chooseStudent

      implicit val token: AuthToken = creator.makeAuthToken()

      withCleanBillingProject(creator, ownerEmails = List(user.email)) { projectName =>
        withGroup("AuthDomain") { groupName =>
          withCleanUp {
            withWorkspace(projectName, "AuthDomainSpec_revoke", Set(groupName)) { workspaceName =>

              checkVisibleNotAccessible(user, projectName, workspaceName)

              api.groups.addUserToGroup(groupName, user.email, GroupRole.Member)
              register cleanUp Try(api.groups.removeUserFromGroup(groupName, user.email, GroupRole.Member)).recover {
                case _: RestException =>
              }
              checkVisibleAndAccessible(user, projectName, workspaceName)

              api.groups.removeUserFromGroup(groupName, user.email, GroupRole.Member)
              checkVisibleNotAccessible(user, projectName, workspaceName)
            }
          }
        }
      }
    }

    "create workspace, + project owner, + group member, - project owner" in {
      val owner = UserPool.chooseProjectOwner
      val creator = owner
      val user = UserPool.chooseStudent

      implicit val token: AuthToken = creator.makeAuthToken()

      withCleanBillingProject(creator, ownerEmails = List(user.email)) { projectName =>
        withGroup("AuthDomain") { groupName =>
          withWorkspace(projectName, "AuthDomainSpec_reject", Set(groupName)) { workspaceName =>

            checkVisibleNotAccessible(user, projectName, workspaceName)

            api.groups.addUserToGroup(groupName, user.email, GroupRole.Member)
            checkVisibleAndAccessible(user, projectName, workspaceName)

            api.billing.removeUserFromBillingProject(projectName, user.email, BillingProjectRole.Owner)
            checkNoAccess(user, projectName, workspaceName)
          }
        }
      }
    }

    "create workspace, + group member, + project owner, - group member" in {
      val owner = UserPool.chooseProjectOwner
      val creator = owner
      val user = UserPool.chooseStudent

      implicit val token: AuthToken = creator.makeAuthToken()

      withCleanBillingProject(creator) { projectName =>
        withGroup("AuthDomain", memberEmails = List(user.email)) { groupName =>
          withCleanUp {
            withWorkspace(projectName, "AuthDomainSpec_reject", Set(groupName)) { workspaceName =>
              checkNoAccess(user, projectName, workspaceName)

              api.billing.addUserToBillingProject(projectName, user.email, BillingProjectRole.Owner)
              register cleanUp Try(api.billing.removeUserFromBillingProject(projectName, user.email, BillingProjectRole.Owner)).recover {
                case _: RestException =>
              }
              checkVisibleAndAccessible(user, projectName, workspaceName)

              api.groups.removeUserFromGroup(groupName, user.email, GroupRole.Member)
              checkVisibleNotAccessible(user, projectName, workspaceName)
            }
          }
        }
      }
    }

    "create workspace, + group member, + project owner, - project owner" in {
      val owner = UserPool.chooseProjectOwner
      val creator = owner
      val user = UserPool.chooseStudent

      implicit val token: AuthToken = creator.makeAuthToken()

      withCleanBillingProject(creator) { projectName =>
        withGroup("AuthDomain", memberEmails = List(user.email)) { groupName =>
          withCleanUp {
            withWorkspace(projectName, "AuthDomainSpec_reject", Set(groupName)) { workspaceName =>
              checkNoAccess(user, projectName, workspaceName)

              api.billing.addUserToBillingProject(projectName, user.email, BillingProjectRole.Owner)
              register cleanUp Try(api.billing.removeUserFromBillingProject(projectName, user.email, BillingProjectRole.Owner)).recover {
                case _: RestException =>
              }
              checkVisibleAndAccessible(user, projectName, workspaceName)

              api.billing.removeUserFromBillingProject(projectName, user.email, BillingProjectRole.Owner)
              checkNoAccess(user, projectName, workspaceName)
            }
          }
        }
      }
    }
  }

  def checkNoAccess(user: Credentials, projectName: String, workspaceName: String): Unit = {
    withWebDriver { implicit driver =>
      withSignIn(user) { workspaceListPage =>
        // Not in workspace list
        workspaceListPage.hasWorkspace(projectName, workspaceName) shouldBe false

        // No direct access
        val workspaceSummaryPage = new WorkspaceSummaryPage(projectName, workspaceName).open
        checkWorkspaceFailure(workspaceSummaryPage, projectName, workspaceName)
      }
    }
  }

  def checkVisibleNotAccessible(user: Credentials, projectName: String, workspaceName: String): Unit = {
    withWebDriver { implicit driver =>
      withSignIn(user) { workspaceListPage =>
        // Looks restricted; implies in workspace list
        eventually { workspaceListPage.looksRestricted(projectName, workspaceName) shouldEqual true }

        // Clicking opens request access modal
        workspaceListPage.clickWorkspaceLink(projectName, workspaceName)
        eventually { workspaceListPage.showsRequestAccessModal() shouldEqual true }
        // TODO: THIS IS BAD! However, the modal does some ajax loading which could cause the button to move causing the click to fail. This needs to be fixed inside RequestAccessModal.
        Thread sleep 500
        new RequestAccessModal().cancel()

        // No direct access
        val workspaceSummaryPage = new WorkspaceSummaryPage(projectName, workspaceName).open
        checkWorkspaceFailure(workspaceSummaryPage, projectName, workspaceName)
      }
    }
  }

  def checkVisibleAndAccessible(user: Credentials, projectName: String, workspaceName: String): Unit = {
    withWebDriver { implicit driver =>
      withSignIn(user) { workspaceListPage =>
        // Looks restricted; implies in workspace list
        eventually { workspaceListPage.looksRestricted(projectName, workspaceName) shouldEqual true }

        // Clicking opens workspace
        workspaceListPage.enterWorkspace(projectName, workspaceName).validateLocation()

        // Direct access also works
        // Navigate somewhere else first otherwise background login status gets lost
        workspaceListPage.open
        new WorkspaceSummaryPage(projectName, workspaceName).open.validateLocation()
      }
    }
  }
}
