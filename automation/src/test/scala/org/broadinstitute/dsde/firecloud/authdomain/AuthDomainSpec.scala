package org.broadinstitute.dsde.firecloud.authdomain

import org.broadinstitute.dsde.firecloud.api.{AclEntry, WorkspaceAccessLevel}
import org.broadinstitute.dsde.firecloud.auth.{AuthToken, AuthTokens}
import org.broadinstitute.dsde.firecloud.pages.{WebBrowserSpec, WorkspaceSummaryPage}
import org.broadinstitute.dsde.firecloud.workspaces.WorkspaceFixtures
import org.broadinstitute.dsde.firecloud.{CleanUp, Config}
import org.scalatest._

class AuthDomainSpec extends FreeSpec with ParallelTestExecution with Matchers
  with CleanUp with WebBrowserSpec with WorkspaceFixtures[AuthDomainSpec] {

  val projectName: String = Config.Projects.common
  val authDomain = "Test-Auth-Domain"

  // Unless otherwise declared, this auth token will be used for API calls.
  implicit val authToken: AuthToken = AuthTokens.fred


  "A workspace with an authorization domain" - {

    "can be created by a user who is in a managed group" in withWebDriver { implicit driver =>
      val workspaceName = "AuthDomainSpec_create_" + randomUuid

      val workspaceListPage = signIn(Config.Users.fred)
      val workspaceDetailPage = workspaceListPage.createWorkspace(projectName, workspaceName, Option(authDomain))
      register cleanUp api.workspaces.delete(projectName, workspaceName)

      workspaceDetailPage.awaitLoaded()
      workspaceDetailPage.ui.readAuthDomainRestrictionMessage should include (authDomain)

      workspaceListPage.open.filter(workspaceName)
      workspaceListPage.ui.looksRestricted(projectName, workspaceName) shouldEqual true
    }

    "when not shared" - {

      "should not be accessible by a user who is not in the authorization domain" in withWebDriver { implicit driver =>
        withWorkspace(projectName, Option("AuthDomainSpec"), Option(authDomain)) { workspaceName =>
          signIn(Config.Users.ron)

          val workspaceSummaryPage = new WorkspaceSummaryPage(projectName, workspaceName)
          go to workspaceSummaryPage
          workspaceSummaryPage.awaitLoaded()
          workspaceSummaryPage.ui.readError() should include(projectName)
          workspaceSummaryPage.ui.readError() should include(workspaceName)
          workspaceSummaryPage.ui.readError() should include("does not exist")
        }
      }

      "should not be accessible by a user who is in the authorization domain" in withWebDriver { implicit driver =>
        withWorkspace(projectName, Option("AuthDomainSpec"), Option(authDomain)) { workspaceName =>
          signIn(Config.Users.george)

          val workspaceSummaryPage = new WorkspaceSummaryPage(projectName, workspaceName)
          go to workspaceSummaryPage
          workspaceSummaryPage.awaitLoaded()
          workspaceSummaryPage.ui.readError() should include(projectName)
          workspaceSummaryPage.ui.readError() should include(workspaceName)
          workspaceSummaryPage.ui.readError() should include("does not exist")
        }
      }

      "should not be visible to a user who is not in the authorization domain" in withWebDriver { implicit driver =>
        withWorkspace(projectName, Option("AuthDomainSpec_share"), Option(authDomain)) { workspaceName =>
          val listPage = signIn(Config.Users.ron)
          listPage.filter(workspaceName)
          listPage.ui.hasWorkspace(projectName, workspaceName) shouldEqual false
        }
      }

      "should not be visible to a user who is in the authorization domain" in withWebDriver { implicit driver =>
        withWorkspace(projectName, Option("AuthDomainSpec_share"), Option(authDomain)) { workspaceName =>
          val listPage = signIn(Config.Users.george)
          listPage.filter(workspaceName)
          listPage.ui.hasWorkspace(projectName, workspaceName) shouldEqual false
        }
      }
    }

    "when shared with a user who is not in the authorization domain" - {

      "should be visible but not accessible" in withWebDriver { implicit driver =>
        withWorkspace(projectName, Option("AuthDomainSpec_reject"), Option(authDomain)) { workspaceName =>
          api.workspaces.updateAcl(projectName, workspaceName, Config.Users.ron.email, WorkspaceAccessLevel.Reader)

          val workspaceListPage = signIn(Config.Users.ron)
          workspaceListPage.filter(workspaceName)
          workspaceListPage.ui.looksRestricted(projectName, workspaceName) shouldEqual true

          workspaceListPage.ui.clickWorkspaceInList(projectName, workspaceName)
          // micro-sleep just long enough to let the app navigate elsewhere if it's going to, which it shouldn't in this case
          Thread sleep 500
          workspaceListPage.validateLocation()
        }
      }
    }

    "when shared with a user who is in the authorization domain" - {

      "should be visible and accessible when shared with single user" in withWebDriver { implicit driver =>
        withWorkspace(projectName, Option("AuthDomainSpec_share"), Option(authDomain), List(AclEntry(Config.Users.george.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
          val listPage = signIn(Config.Users.george)
          listPage.filter(workspaceName)
          listPage.ui.looksRestricted(projectName, workspaceName) shouldEqual true

          val summaryPage = listPage.openWorkspaceDetails(projectName, workspaceName).awaitLoaded()
          summaryPage.ui.readAuthDomainRestrictionMessage should include (authDomain)
        }
      }

      "should be visible and accessible when shared with a group" in withWebDriver { implicit driver =>
        withWorkspace(projectName, Option("AuthDomainSpec_share"), Option(authDomain), List(AclEntry("GROUP_dbGapAuthorizedUsers@dev.test.firecloud.org", WorkspaceAccessLevel.Reader))) { workspaceName =>
          val listPage = signIn(Config.Users.fred)
          listPage.filter(workspaceName)
          listPage.ui.looksRestricted(projectName, workspaceName) shouldEqual true

          val summaryPage = listPage.openWorkspaceDetails(projectName, workspaceName).awaitLoaded()
          summaryPage.ui.readAuthDomainRestrictionMessage should include (authDomain)
        }
      }

      "can be cloned" in withWebDriver { implicit driver =>
        withWorkspace(projectName, Option("AuthDomainSpec_share"), Option(authDomain), List(AclEntry(Config.Users.george.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
          val listPage = signIn(Config.Users.george)
          val summaryPage = listPage.openWorkspaceDetails(projectName, workspaceName)

          val cloneWorkspaceName = workspaceName + "_clone"
          register cleanUp { api.workspaces.delete(projectName, cloneWorkspaceName)(AuthTokens.george) }
          val cloneSummaryPage = summaryPage.cloneWorkspace(projectName, cloneWorkspaceName)
          cloneSummaryPage.ui.readWorkspaceName should be(cloneWorkspaceName)
          cloneSummaryPage.ui.readAuthDomainRestrictionMessage should include(authDomain)
        }
      }
    }

    "cannot lose its authorization domain when cloned" in withWebDriver { implicit driver =>
      withWorkspace(projectName, Option("AuthDomainSpec_share"), Option(authDomain)) { workspaceName =>
        api.workspaces.updateAcl(projectName, workspaceName,
          Config.Users.george.email, WorkspaceAccessLevel.Reader)

        val listPage = signIn(Config.Users.george)
        val summaryPage = listPage.openWorkspaceDetails(projectName, workspaceName)

        val cloneWorkspaceName = workspaceName + "_clone"
        val cloneModal = summaryPage.ui.clickCloneButton()
        cloneModal.ui.readPresetAuthDomain() should be(Some(authDomain))
        cloneModal.cloneWorkspace(projectName, cloneWorkspaceName)
        register cleanUp { api.workspaces.delete(projectName, cloneWorkspaceName)(AuthTokens.george) }
        cloneModal.awaitCloneComplete()
        val cloneSummaryPage = new WorkspaceSummaryPage(projectName, cloneWorkspaceName).awaitLoaded()
        cloneSummaryPage.ui.readWorkspaceName should be(cloneWorkspaceName)
        cloneSummaryPage.ui.readAuthDomainRestrictionMessage should include(authDomain)
      }
    }
  }
}
