package org.broadinstitute.dsde.firecloud.test.security

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.api.Orchestration.billing.BillingProjectRole
import org.broadinstitute.dsde.firecloud.api.Orchestration.groups.GroupRole
import org.broadinstitute.dsde.firecloud.api.{AclEntry, WorkspaceAccessLevel}
import org.broadinstitute.dsde.firecloud.auth.{AuthToken, UserAuthToken}
import org.broadinstitute.dsde.firecloud.config.{Config, Credentials, UserPool}
import org.broadinstitute.dsde.firecloud.fixture.{BillingFixtures, GroupFixtures, UserFixtures, WorkspaceFixtures}
import org.broadinstitute.dsde.firecloud.page.billing.BillingManagementPage
import org.broadinstitute.dsde.firecloud.page.workspaces.RequestAccessModal
import org.broadinstitute.dsde.firecloud.page.workspaces.summary.WorkspaceSummaryPage
import org.broadinstitute.dsde.firecloud.test.{CleanUp, WebBrowserSpec}
import org.openqa.selenium.WebDriver
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
  with BillingFixtures with GroupFixtures
  with UserFixtures {

  val projectName: String = Config.Projects.common
  /*
   * Unless otherwise declared, this auth token will be used for API calls.
   * We are using a curator to prevent collisions with users in tests (who are Students and AuthDomainUsers), not
   *  because we specifically need a curator.
   */
  val defaultUser: Credentials = UserPool.chooseCurator
  val authTokenDefault: AuthToken = defaultUser.makeAuthToken()

  private def checkWorkspaceFailure(workspaceSummaryPage: WorkspaceSummaryPage, workspaceName: String): Unit = {
    val error = workspaceSummaryPage.readError()
    error should include(projectName)
    error should include(workspaceName)
    error should include("does not exist")
  }

  private def checkWorkspaceFailure(workspaceSummaryPage: WorkspaceSummaryPage, projectName: String, workspaceName: String): Unit = {
    val error = workspaceSummaryPage.readError()
    error should include(projectName)
    error should include(workspaceName)
    error should include("does not exist")
  }

  "A workspace with an authorization domain" - {
    "with one group inside of it" - {
      "can be created" in withWebDriver { implicit driver =>
        val user = UserPool.chooseAuthDomainUser
        implicit val authToken: AuthToken = authTokenDefault
        withGroup("AuthDomain", List(user.email)) { authDomainName =>
          withCleanUp {

            withSignIn(user) { listPage =>
              val workspaceName = "AuthDomainSpec_create_" + randomUuid
              register cleanUp api.workspaces.delete(projectName, workspaceName)
              val workspaceSummaryPage = listPage.createWorkspace(projectName, workspaceName, Set(authDomainName))

              workspaceSummaryPage.readAuthDomainGroups should include(authDomainName)
            }
          }
        }
      }

      "can be cloned and retain the auth domain" in withWebDriver { implicit driver =>
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

                register cleanUp {
                  api.workspaces.delete(projectName, cloneWorkspaceName)(authToken)
                }


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

    //MULTI-GROUP AUTH DOMAIN TESTS

    "with multiple groups inside of it" - {
      "can be created" in withWebDriver { implicit driver =>
        val user = UserPool.chooseAuthDomainUser
        implicit val authToken: AuthToken = authTokenDefault
        withGroup("AuthDomain", List(user.email)) { groupOneName =>
          withGroup("AuthDomain", List(user.email)) { groupTwoName =>
            withCleanUp {
              withSignIn(user) { workspaceListPage =>
                val workspaceName = "AuthDomainSpec_create_" + randomUuid
                register cleanUp api.workspaces.delete(projectName, workspaceName)
                val workspaceSummaryPage = workspaceListPage.createWorkspace(projectName, workspaceName, Set(groupOneName, groupTwoName))

                workspaceSummaryPage.readAuthDomainGroups should include(groupOneName)
                workspaceSummaryPage.readAuthDomainGroups should include(groupTwoName)
              }
            }
          }
        }
      }
      "can be cloned and retain the auth domain" in withWebDriver { implicit driver =>
        val user = UserPool.chooseAuthDomainUser
        implicit val authToken: AuthToken = authTokenDefault
        withGroup("AuthDomain", List(user.email)) { groupOneName =>
          withGroup("AuthDomain", List(user.email)) { groupTwoName =>
            withWorkspace(projectName, "AuthDomainSpec_share", Set(groupOneName, groupTwoName), List(AclEntry(user.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
              withCleanUp {
                withSignIn(user) { listPage =>
                  val summaryPage = listPage.enterWorkspace(projectName, workspaceName)
                  val cloneWorkspaceName = workspaceName + "_clone"
                  val cloneModal = summaryPage.clickCloneButton()
                  cloneModal.readLockedAuthDomainGroups() should contain(groupOneName)
                  cloneModal.readLockedAuthDomainGroups() should contain(groupTwoName)

                  register cleanUp {
                    api.workspaces.delete(projectName, cloneWorkspaceName)(user.makeAuthToken())
                  }


                  val cloneSummaryPage = cloneModal.cloneWorkspace(projectName, cloneWorkspaceName)
                  cloneSummaryPage.validateWorkspace shouldEqual true
                  cloneSummaryPage.readAuthDomainGroups should include(groupOneName)
                  cloneSummaryPage.readAuthDomainGroups should include(groupTwoName)
                }
              }
            }
          }
        }
      }
      "can be cloned and have a group added to the auth domain" in withWebDriver { implicit driver =>
        val user = UserPool.chooseAuthDomainUser
        implicit val authToken: AuthToken = authTokenDefault
        withGroup("AuthDomain", List(user.email)) { groupOneName =>
          withGroup("AuthDomain", List(user.email)) { groupTwoName =>
            withGroup("AuthDomain", List(user.email)) { groupThreeName =>
              withWorkspace(projectName, "AuthDomainSpec_share", Set(groupOneName, groupTwoName), List(AclEntry(user.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
                withCleanUp {
                  withSignIn(user) { listPage =>
                    val cloneWorkspaceName = workspaceName + "_clone"
                    val summaryPage = listPage.enterWorkspace(projectName, workspaceName)

                    summaryPage.cloneWorkspace(projectName, cloneWorkspaceName, Set(groupThreeName))

                    register cleanUp {
                      api.workspaces.delete(projectName, cloneWorkspaceName)(user.makeAuthToken())
                    }

                    summaryPage.readAuthDomainGroups should include(groupOneName)
                    summaryPage.readAuthDomainGroups should include(groupTwoName)
                    summaryPage.readAuthDomainGroups should include(groupThreeName)
                  }
                }
              }
            }
          }
        }
      }
      "looks restricted in the workspace list page" in withWebDriver { implicit driver =>
        val user = UserPool.chooseAuthDomainUser
        implicit val authToken: AuthToken = user.makeAuthToken()
        withGroup("AuthDomain", List(user.email)) { groupOneName =>
          withGroup("AuthDomain", List(user.email)) { groupTwoName =>
            withWorkspace(projectName, "AuthDomainSpec_create", Set(groupOneName, groupTwoName)) { workspaceName =>
              withCleanUp {
                withSignIn(user) { workspaceListPage =>
                  workspaceListPage.looksRestricted(projectName, workspaceName) shouldEqual true
                }
              }
            }
          }
        }
      }
      "contains the list of auth domain groups in the workspace summary page" in withWebDriver { implicit driver =>
        val user = UserPool.chooseAuthDomainUser
        implicit val authToken: AuthToken = authTokenDefault
        withGroup("AuthDomain", List(user.email)) { groupOneName =>
          withGroup("AuthDomain", List(user.email)) { groupTwoName =>
            withCleanUp {
              withSignIn(user) { workspaceListPage =>
                val workspaceName = "AuthDomainSpec_create_" + randomUuid
                register cleanUp api.workspaces.delete(projectName, workspaceName)
                val workspaceSummaryPage = workspaceListPage.createWorkspace(projectName, workspaceName, Set(groupOneName, groupTwoName))

                workspaceSummaryPage.readAuthDomainGroups should include(groupOneName)
                workspaceSummaryPage.readAuthDomainGroups should include(groupTwoName)
              }
            }
          }
        }
      }

      "when the user is in none of the groups" - {
        "when shared with them" - {
          "can be seen but is not accessible" in withWebDriver { implicit driver =>
            val user = UserPool.chooseStudent
            implicit val authToken: AuthToken = authTokenDefault
            withGroup("AuthDomain") { groupOneName =>
              withGroup("AuthDomain") { groupTwoName =>
                withWorkspace(projectName, "AuthDomainSpec_reject", Set(groupOneName, groupTwoName), List(AclEntry(user.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
                  withSignIn(user) { workspaceListPage =>
                    workspaceListPage.clickWorkspaceLink(projectName, workspaceName)
                    workspaceListPage.showsRequestAccessModal shouldEqual true
                    workspaceListPage.validateLocation()
                    // TODO: add assertions for the new "request access" modal
                    // TODO: finish this test somewhere we can access the sign out button
                  }
                }
              }
            }
          }
        }
        "when not shared with them" - {
          "cannot be seen and is not accessible" in withWebDriver { implicit driver =>
            val user = UserPool.chooseStudent
            implicit val authToken: AuthToken = authTokenDefault
            withGroup("AuthDomain") { groupOneName =>
              withGroup("AuthDomain") { groupTwoName =>
                withWorkspace(projectName, "AuthDomainSpec", Set(groupOneName, groupTwoName)) { workspaceName =>
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
      }

      "when the user is in one of the groups" - {
        "when shared with them" - {
          "can be seen but is not accessible" in withWebDriver { implicit driver =>
            val user = UserPool.chooseStudent
            implicit val authToken: AuthToken = authTokenDefault
            withGroup("AuthDomain") { groupOneName =>
              withGroup("AuthDomain", List(user.email)) { groupTwoName =>
                withWorkspace(projectName, "AuthDomainSpec_reject", Set(groupOneName, groupTwoName), List(AclEntry(user.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
                  withSignIn(user) { workspaceListPage =>
                    workspaceListPage.clickWorkspaceLink(projectName, workspaceName)
                    workspaceListPage.showsRequestAccessModal shouldEqual true
                    workspaceListPage.validateLocation()
                    // TODO: add assertions for the new "request access" modal
                    // TODO: finish this test somewhere we can access the sign out button
                  }
                }
              }
            }
          }
          "when the user is a billing project owner" - {
            "can be seen but is not accessible" in withWebDriver { implicit driver =>
              val user = UserPool.chooseProjectOwner
              implicit val authToken: AuthToken = authTokenDefault
              withGroup("AuthDomain") { authDomainName =>
                withWorkspace(projectName, "AuthDomainSpec_reject", Set(authDomainName)) { workspaceName =>
                  withSignIn(user) { workspaceListPage =>
                    workspaceListPage.clickWorkspaceLink(projectName, workspaceName)
                    workspaceListPage.showsRequestAccessModal shouldEqual true
                    workspaceListPage.validateLocation()
                    // TODO: finish this test somewhere we can access the sign out button
                  }
                }
              }
            }
          }
        }
        "when not shared with them" - {
          "cannot be seen and is not accessible" in withWebDriver { implicit driver =>
            val user = UserPool.chooseStudent
            implicit val authToken: AuthToken = authTokenDefault
            withGroup("AuthDomain") { groupOneName =>
              withGroup("AuthDomain", List(user.email)) { groupTwoName =>
                withWorkspace(projectName, "AuthDomainSpec", Set(groupOneName, groupTwoName)) { workspaceName =>
                  withSignIn(user) { workspaceListPage =>
                    workspaceListPage.hasWorkspace(projectName, workspaceName) shouldEqual false

                    val workspaceSummaryPage = new WorkspaceSummaryPage(projectName, workspaceName).open
                    checkWorkspaceFailure(workspaceSummaryPage, workspaceName)
                  }
                }
              }
            }
          }
          "when the user is a billing project owner" - {
            "can be seen but is not accessible" in withWebDriver { implicit driver =>
              val user = UserPool.chooseProjectOwner
              implicit val authToken: AuthToken = authTokenDefault
              withGroup("AuthDomain") { authDomainName =>
                withWorkspace(projectName, "AuthDomainSpec_reject", Set(authDomainName)) { workspaceName =>
                  withSignIn(user) { workspaceListPage =>
                    workspaceListPage.clickWorkspaceLink(projectName, workspaceName)
                    workspaceListPage.showsRequestAccessModal shouldEqual true
                    workspaceListPage.validateLocation()
                  }
                }
              }
            }
          }
        }
      }

      "when the user is in all of the groups" - {
        "when shared with them" - {
          "can be seen and is accessible" in withWebDriver { implicit driver =>

            val user = UserPool.chooseStudent
            implicit val authToken: AuthToken = authTokenDefault
            withGroup("AuthDomain", List(user.email)) { groupOneName =>
              withGroup("AuthDomain", List(user.email)) { groupTwoName =>
                withWorkspace(projectName, "AuthDomainSpec_share", Set(groupOneName, groupTwoName), List(AclEntry(user.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
                  withSignIn(user) { listPage =>
                    val summaryPage = listPage.enterWorkspace(projectName, workspaceName)
                    summaryPage.readAuthDomainGroups should include(groupOneName)
                    summaryPage.readAuthDomainGroups should include(groupTwoName)
                  }
                }
              }
            }
          }
          "and given writer access" - {
            "the user has correct permissions" in withWebDriver { implicit driver =>
              val user = UserPool.chooseStudent
              implicit val authToken: AuthToken = authTokenDefault
              withGroup("AuthDomain", List(user.email)) { groupOneName =>
                withGroup("AuthDomain", List(user.email)) { groupTwoName =>
                  withWorkspace(projectName, "AuthDomainSpec_create", Set(groupOneName, groupTwoName), List(AclEntry(user.email, WorkspaceAccessLevel.Writer))) { workspaceName =>
                    withCleanUp {
                      withSignIn(user) { workspaceListPage =>
                        val summaryPage = workspaceListPage.enterWorkspace(projectName, workspaceName)
                        summaryPage.readAccessLevel() should be(WorkspaceAccessLevel.Writer)
                      }
                    }
                  }
                }
              }
            }
          }
          "when the user is a billing project owner" - {
            "can be seen and is accessible" in withWebDriver { implicit driver =>
              val user = UserPool.chooseProjectOwner
              implicit val authToken: AuthToken = authTokenDefault
              withGroup("AuthDomain", List(user.email)) { groupOneName =>
                withGroup("AuthDomain", List(user.email)) { groupTwoName =>
                  withWorkspace(projectName, "AuthDomainSpec_share", Set(groupOneName, groupTwoName)) { workspaceName =>
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
        "when shared with one of the groups in the auth domain" - {
          "can be seen and is accessible by group member who is a member of both auth domain groups" in withWebDriver { implicit driver =>
            val user = UserPool.chooseStudent
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
          "can be seen but is not accessible by group member who is a member of only one auth domain group" in withWebDriver { implicit driver =>
            val user = UserPool.chooseStudent
            implicit val authToken: AuthToken = authTokenDefault
            withGroup("AuthDomain", List(user.email)) { groupOneName =>
              withGroup("AuthDomain") { groupTwoName =>
                withWorkspace(projectName, "AuthDomainSpec_share", Set(groupOneName, groupTwoName), List(AclEntry(groupNameToEmail(groupOneName), WorkspaceAccessLevel.Reader))) { workspaceName =>
                  withSignIn(user) { workspaceListPage =>
                    workspaceListPage.hasWorkspace(projectName, workspaceName) shouldEqual true

                    val workspaceSummaryPage = new WorkspaceSummaryPage(projectName, workspaceName).open
                    checkWorkspaceFailure(workspaceSummaryPage, workspaceName)
                  }
                }
              }
            }
          }
        }
        "when not shared with them" - {
          "cannot be seen and is not accessible" in withWebDriver { implicit driver =>
            val user = UserPool.chooseStudent
            implicit val authToken: AuthToken = authTokenDefault
            withGroup("AuthDomain", List(user.email)) { groupOneName =>
              withGroup("AuthDomain", List(user.email)) { groupTwoName =>
                withWorkspace(projectName, "AuthDomainSpec_reject", Set(groupOneName, groupTwoName)) { workspaceName =>
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
      }
    }
  }

  "removing permissions from billing project owners for workspaces with auth domains" - {
    "+ project owner, + group member, create workspace, - group member" in withWebDriver { implicit driver =>
      val owner = UserPool.chooseProjectOwner
      val creator = UserPool.chooseStudent
      val user = owner

      implicit val token: AuthToken = creator.makeAuthToken()

      val projectName: String = Config.Projects.default
      withGroup("AuthDomain", List(user.email)) { groupName =>
        withWorkspace(projectName, "AuthDomainSpec_revoke", Set(groupName)) { workspaceName =>
          checkVisibleAndAccessible(user, projectName, workspaceName)

          api.groups.removeUserFromGroup(groupName, user.email, GroupRole.Member)
          checkVisibleNotAccessible(user, projectName, workspaceName)
        }
      }
    }

    "+ project owner, + group member, create workspace, - project owner" in withWebDriver { implicit driver =>
      val owner = UserPool.chooseProjectOwner
      val creator = owner
      val user = UserPool.chooseStudent

      implicit val token: AuthToken = creator.makeAuthToken()

      withBillingProject("auth-domain-spec") { projectName =>
        api.billing.addUserToBillingProject(projectName, user.email, BillingProjectRole.Owner)
        withGroup("AuthDomain", List(user.email)) { groupName =>
          withWorkspace(projectName, "AuthDomainSpec_revoke", Set(groupName)) { workspaceName =>
            checkVisibleAndAccessible(user, projectName, workspaceName)

            api.billing.removeUserFromBillingProject(projectName, user.email, BillingProjectRole.Owner)
            checkNoAccess(user, projectName, workspaceName)
          }
        }
      }
    }

    "+ project owner, create workspace, + group member, - group member" in withWebDriver { implicit driver =>
      val owner = UserPool.chooseProjectOwner
      val creator = UserPool.chooseStudent
      val user = owner

      implicit val token: AuthToken = creator.makeAuthToken()

      val projectName: String = Config.Projects.default
      withGroup("AuthDomain") { groupName =>
        withWorkspace(projectName, "AuthDomainSpec_revoke", Set(groupName)) { workspaceName =>
          checkVisibleNotAccessible(user, projectName, workspaceName)

          api.groups.addUserToGroup(groupName, user.email, GroupRole.Member)
          checkVisibleAndAccessible(user, projectName, workspaceName)

          api.groups.removeUserFromGroup(groupName, user.email, GroupRole.Member)
          checkVisibleNotAccessible(user, projectName, workspaceName)
        }
      }
    }

    "+ project owner, create workspace, + group member, - project owner" in withWebDriver { implicit driver =>
      val owner = UserPool.chooseProjectOwner
      val creator = owner
      val user = UserPool.chooseStudent

      implicit val token: AuthToken = creator.makeAuthToken()

      withBillingProject("auth-domain-spec") { projectName =>
        api.billing.addUserToBillingProject(projectName, user.email, BillingProjectRole.Owner)
        withGroup("AuthDomain") { groupName =>
          withCleanUp {
            withWorkspace(projectName, "AuthDomainSpec_revoke", Set(groupName)) { workspaceName =>
              checkVisibleNotAccessible(user, projectName, workspaceName)

              api.groups.addUserToGroup(groupName, user.email, GroupRole.Member)
              register cleanUp api.groups.removeUserFromGroup(groupName, user.email, GroupRole.Member)

              checkVisibleAndAccessible(user, projectName, workspaceName)

              api.billing.removeUserFromBillingProject(projectName, user.email, BillingProjectRole.Owner)
              checkNoAccess(user, projectName, workspaceName)

            }
          }
        }
      }
    }

    "+ group member, create workspace, + project owner, - group member" in withWebDriver { implicit driver =>
      val owner = UserPool.chooseProjectOwner
      val creator = owner
      val user = UserPool.chooseStudent

      implicit val token: AuthToken = creator.makeAuthToken()

      withBillingProject("auth-domain-spec") { projectName =>
        withGroup("AuthDomain", List(user.email)) { groupName =>
          withWorkspace(projectName, "AuthDomainSpec_revoke", Set(groupName)) { workspaceName =>
            checkNoAccess(user, projectName, workspaceName)

            api.billing.addUserToBillingProject(projectName, user.email, BillingProjectRole.Owner)
            checkVisibleAndAccessible(user, projectName, workspaceName)

            api.groups.removeUserFromGroup(groupName, user.email, GroupRole.Member)
            checkVisibleNotAccessible(user, projectName, workspaceName)
          }
        }
      }
    }

    "+ group member, create workspace, + project owner, - project owner" in withWebDriver { implicit driver =>
      val owner = UserPool.chooseProjectOwner
      val creator = owner
      val user = UserPool.chooseStudent

      implicit val token: AuthToken = creator.makeAuthToken()

      withBillingProject("auth-domain-spec") { projectName =>
        withGroup("AuthDomain", List(user.email)) { groupName =>
          withWorkspace(projectName, "AuthDomainSpec_revoke", Set(groupName)) { workspaceName =>
            checkNoAccess(user, projectName, workspaceName)

            api.billing.addUserToBillingProject(projectName, user.email, BillingProjectRole.Owner)
            checkVisibleAndAccessible(user, projectName, workspaceName)

            api.billing.removeUserFromBillingProject(projectName, user.email, BillingProjectRole.Owner)
            checkNoAccess(user, projectName, workspaceName)
          }
        }
      }
    }

    "create workspace, + project owner, + group member, - group member" in withWebDriver { implicit driver =>
      val owner = UserPool.chooseProjectOwner
      val creator = owner
      val user = UserPool.chooseStudent

      implicit val token: AuthToken = creator.makeAuthToken()

      withBillingProject("auth-domain-spec") { projectName =>
        withGroup("AuthDomain") { groupName =>
          withWorkspace(projectName, "AuthDomainSpec_revoke", Set(groupName)) { workspaceName =>
            api.billing.addUserToBillingProject(projectName, user.email, BillingProjectRole.Owner)
            checkVisibleNotAccessible(user, projectName, workspaceName)

            api.groups.addUserToGroup(groupName, user.email, GroupRole.Member)
            checkVisibleAndAccessible(user, projectName, workspaceName)

            api.groups.removeUserFromGroup(groupName, user.email, GroupRole.Member)
            checkVisibleNotAccessible(user, projectName, workspaceName)
          }
        }
      }
    }

    "create workspace, + project owner, + group member, - project owner" in withWebDriver { implicit driver =>
      val owner = UserPool.chooseProjectOwner
      val creator = owner
      val user = UserPool.chooseStudent

      implicit val token: AuthToken = creator.makeAuthToken()

      withBillingProject("auth-domain-spec") { projectName =>
        withGroup("AuthDomain") { groupName =>
          withCleanUp {
            withWorkspace(projectName, "AuthDomainSpec_reject", Set(groupName)) { workspaceName =>
              api.billing.addUserToBillingProject(projectName, user.email, BillingProjectRole.Owner)
              checkVisibleNotAccessible(user, projectName, workspaceName)

              api.groups.addUserToGroup(groupName, user.email, GroupRole.Member)
              register cleanUp api.groups.removeUserFromGroup(groupName, user.email, GroupRole.Member)

              checkVisibleAndAccessible(user, projectName, workspaceName)

              api.billing.removeUserFromBillingProject(projectName, user.email, BillingProjectRole.Owner)
              checkNoAccess(user, projectName, workspaceName)
            }
          }
        }
      }
    }

    "create workspace, + group member, + project owner, - group member" in withWebDriver { implicit driver =>
      val owner = UserPool.chooseProjectOwner
      val creator = owner
      val user = UserPool.chooseStudent

      implicit val token: AuthToken = creator.makeAuthToken()

      withBillingProject("auth-domain-spec") { projectName =>
        withGroup("AuthDomain") { groupName =>
          withWorkspace(projectName, "AuthDomainSpec_reject", Set(groupName)) { workspaceName =>
            api.groups.addUserToGroup(groupName, user.email, GroupRole.Member)
            checkNoAccess(user, projectName, workspaceName)

            api.billing.addUserToBillingProject(projectName, user.email, BillingProjectRole.Owner)
            checkVisibleAndAccessible(user, projectName, workspaceName)

            api.groups.removeUserFromGroup(groupName, user.email, GroupRole.Member)
            checkVisibleNotAccessible(user, projectName, workspaceName)
          }
        }
      }
    }

    "create workspace, + group member, + project owner, - project owner" in withWebDriver { implicit driver =>
      val owner = UserPool.chooseProjectOwner
      val creator = owner
      val user = UserPool.chooseStudent

      implicit val token: AuthToken = creator.makeAuthToken()

      withBillingProject("auth-domain-spec") { projectName =>
        withGroup("AuthDomain") { groupName =>
          withCleanUp {
            withWorkspace(projectName, "AuthDomainSpec_reject", Set(groupName)) { workspaceName =>
              api.groups.addUserToGroup(groupName, user.email, GroupRole.Member)
              register cleanUp api.groups.removeUserFromGroup(groupName, user.email, GroupRole.Member)

              checkNoAccess(user, projectName, workspaceName)

              api.billing.addUserToBillingProject(projectName, user.email, BillingProjectRole.Owner)
              checkVisibleAndAccessible(user, projectName, workspaceName)

              api.billing.removeUserFromBillingProject(projectName, user.email, BillingProjectRole.Owner)
              checkNoAccess(user, projectName, workspaceName)
            }
          }
        }
      }
    }
  }

  def checkNoAccess(user: Credentials, projectName: String, workspaceName: String)
                   (implicit webDriver: WebDriver): Unit = {
    withSignIn(user) { workspaceListPage =>
      // Not in workspace list
      workspaceListPage.hasWorkspace(projectName, workspaceName) shouldBe false

      // No direct access
      val workspaceSummaryPage = new WorkspaceSummaryPage(projectName, workspaceName).open
      checkWorkspaceFailure(workspaceSummaryPage, projectName, workspaceName)
    }
  }

  def checkVisibleNotAccessible(user: Credentials, projectName: String, workspaceName: String)
                               (implicit webDriver: WebDriver): Unit = {
    withSignIn(user) { workspaceListPage =>
      // Looks restricted; implies in workspace list
      workspaceListPage.looksRestricted(projectName, workspaceName) shouldEqual true

      // Clicking opens request access modal
      workspaceListPage.clickWorkspaceLink(projectName, workspaceName)
      workspaceListPage.showsRequestAccessModal() shouldEqual true
      // TODO: THIS IS BAD! However, the modal does some ajax loading which could cause the button to move causing the click to fail. This needs to be fixed inside RequestAccessModal.
      Thread sleep 500
      new RequestAccessModal().cancel()

      // No direct access
      val workspaceSummaryPage = new WorkspaceSummaryPage(projectName, workspaceName).open
      checkWorkspaceFailure(workspaceSummaryPage, projectName, workspaceName)
    }
  }

  def checkVisibleAndAccessible(user: Credentials, projectName: String, workspaceName: String)
                               (implicit webDriver: WebDriver): Unit = {
    withSignIn(user) { workspaceListPage =>
      // Looks restricted; implies in workspace list
      workspaceListPage.looksRestricted(projectName, workspaceName) shouldEqual true

      // Clicking opens workspace
      workspaceListPage.enterWorkspace(projectName, workspaceName).validateLocation()

      // Direct access also works
      // Navigate somewhere else first otherwise background login status gets lost
      workspaceListPage.open
      new WorkspaceSummaryPage(projectName, workspaceName).open.validateLocation()
    }
  }
}
