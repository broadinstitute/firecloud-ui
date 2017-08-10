package org.broadinstitute.dsde.firecloud.test.security

import org.broadinstitute.dsde.firecloud.api.Orchestration.billing.BillingProjectRole
import org.broadinstitute.dsde.firecloud.api.{AclEntry, WorkspaceAccessLevel}
import org.broadinstitute.dsde.firecloud.config.{AuthToken, AuthTokens, Config}
import org.broadinstitute.dsde.firecloud.fixture.{GroupFixtures, WorkspaceFixtures}
import org.broadinstitute.dsde.firecloud.page.billing.BillingManagementPage
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
    "with one group inside of it" - {
      "can be created" in withWebDriver { implicit driver =>
        withGroup("AuthDomainSpec") { authDomainName =>
          withCleanUp {
            val workspaceListPage = signIn(Config.Users.fred)

            val workspaceName = "AuthDomainSpec_create_" + randomUuid
            register cleanUp api.workspaces.delete(projectName, workspaceName)
            val workspaceDetailPage = workspaceListPage.createWorkspace(projectName, workspaceName, Set(authDomainName)).awaitLoaded()

            workspaceDetailPage.ui.readAuthDomainGroups should include(authDomainName)

            workspaceListPage.open.filter(workspaceName)
            workspaceListPage.ui.looksRestricted(projectName, workspaceName) shouldEqual true
          }
        }
      }

      "can be cloned and retain the auth domain" in withWebDriver { implicit driver =>
        withGroup("AuthDomainSpec", List(Config.Users.george.email)) { authDomainName =>
          withWorkspace(projectName, "AuthDomainSpec_share", Set(authDomainName)) { workspaceName =>
            withCleanUp {
              api.workspaces.updateAcl(projectName, workspaceName,
                Config.Users.george.email, WorkspaceAccessLevel.Reader)

              val listPage = signIn(Config.Users.george)
              val summaryPage = listPage.openWorkspaceDetails(projectName, workspaceName)

              val cloneWorkspaceName = workspaceName + "_clone"
              val cloneModal = summaryPage.ui.clickCloneButton()
              cloneModal.ui.readLockedAuthDomainGroups() should contain(authDomainName)

              register cleanUp {
                api.workspaces.delete(projectName, cloneWorkspaceName)(AuthTokens.george)
              }
              cloneModal.cloneWorkspace(projectName, cloneWorkspaceName)
              cloneModal.cloneWorkspaceWait()
              summaryPage.cloneWorkspaceWait()

              val cloneSummaryPage = new WorkspaceSummaryPage(projectName, cloneWorkspaceName).awaitLoaded()
              cloneSummaryPage.ui.readWorkspaceName should be(cloneWorkspaceName)
              cloneSummaryPage.ui.readAuthDomainGroups should include(authDomainName)
            }
          }
        }
      }

      "when the user is not inside of the group" - {
        "when the workspace is shared with them" - {
          "can be seen but is not accessible" in withWebDriver { implicit driver =>
            withGroup("AuthDomainSpec") { authDomainName =>
              withWorkspace(projectName, "AuthDomainSpec_reject", Set(authDomainName)) { workspaceName =>
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
          "when the user is a billing project owner" - {
            "can be seen but is not accessible" in withWebDriver { implicit driver =>
              withGroup("AuthDomainSpec") { authDomainName =>
                withWorkspace(projectName, "AuthDomainSpec_reject", Set(authDomainName)) { workspaceName =>
                  val workspaceListPage = signIn(Config.Users.hermione)
                  workspaceListPage.filter(workspaceName)
                  workspaceListPage.ui.looksRestricted(projectName, workspaceName) shouldEqual true

                  workspaceListPage.ui.clickWorkspaceInList(projectName, workspaceName)
                  // micro-sleep just long enough to let the app navigate elsewhere if it's going to, which it shouldn't in this case
                  Thread sleep 500
                  workspaceListPage.validateLocation()
                }
              }
            }
          }
        }
        "when the workspace is not shared with them" - {
          "cannot be seen and is not accessible" in withWebDriver { implicit driver =>
            withGroup("AuthDomainSpec") { authDomainName =>
              withWorkspace(projectName, "AuthDomainSpec", Set(authDomainName)) { workspaceName =>
                val workspaceListPage = signIn(Config.Users.ron)

                workspaceListPage.filter(workspaceName)
                workspaceListPage.ui.hasWorkspace(projectName, workspaceName) shouldEqual false

                val workspaceSummaryPage = new WorkspaceSummaryPage(projectName, workspaceName)
                go to workspaceSummaryPage
                workspaceSummaryPage.awaitLoaded()
                workspaceSummaryPage.ui.readError() should include(projectName)
                workspaceSummaryPage.ui.readError() should include(workspaceName)
                workspaceSummaryPage.ui.readError() should include("does not exist")
              }
            }
          }
        }
      }

      "when the user is inside of the group" - {
        "when the workspace is shared with them" - {
          "can be seen and is accessible" in withWebDriver { implicit driver =>
            withGroup("AuthDomainSpec", List(Config.Users.george.email)) { authDomainName =>
              withWorkspace(projectName, "AuthDomainSpec_share", Set(authDomainName), List(AclEntry(Config.Users.george.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
                val listPage = signIn(Config.Users.george)
                listPage.filter(workspaceName)
                listPage.ui.looksRestricted(projectName, workspaceName) shouldEqual true

                val summaryPage = listPage.openWorkspaceDetails(projectName, workspaceName).awaitLoaded()
                summaryPage.ui.readAuthDomainGroups should include(authDomainName)
              }
            }
          }
          "when the user is a billing project owner" - {
            "can be seen but is not accessible" in withWebDriver { implicit driver =>
              withGroup("AuthDomainSpec") { groupOneName =>
                withWorkspace(projectName, "AuthDomainSpec_reject", Set(groupOneName)) { workspaceName =>
                  val workspaceListPage = signIn(Config.Users.hermione)
                  workspaceListPage.filter(workspaceName)
                  workspaceListPage.ui.looksRestricted(projectName, workspaceName) shouldEqual true

                  workspaceListPage.ui.clickWorkspaceInList(projectName, workspaceName)
                  // micro-sleep just long enough to let the app navigate elsewhere if it's going to, which it shouldn't in this case
                  Thread sleep 500
                  workspaceListPage.validateLocation()
                }
              }
            }
          }
        }
        "when the workspace is not shared with them" - {
          "cannot be seen and is not accessible" in withWebDriver { implicit driver =>
            withGroup("AuthDomainSpec", List(Config.Users.george.email)) { authDomainName =>
              withWorkspace(projectName, "AuthDomainSpec", Set(authDomainName)) { workspaceName =>
                val workspaceListPage = signIn(Config.Users.george)
                workspaceListPage.filter(workspaceName)
                workspaceListPage.ui.hasWorkspace(projectName, workspaceName) shouldEqual false

                val workspaceSummaryPage = new WorkspaceSummaryPage(projectName, workspaceName)
                go to workspaceSummaryPage
                workspaceSummaryPage.awaitLoaded()
                workspaceSummaryPage.ui.readError() should include(projectName)
                workspaceSummaryPage.ui.readError() should include(workspaceName)
                workspaceSummaryPage.ui.readError() should include("does not exist")
              }
            }
          }
          "when the user is a billing project owner" - {
            "can be seen and is accessible" in withWebDriver { implicit driver =>
              withGroup("AuthDomainSpec", List(Config.Users.hermione.email)) { authDomainName =>
                withWorkspace(projectName, "AuthDomainSpec_share", Set(authDomainName)) { workspaceName =>
                  val listPage = signIn(Config.Users.hermione)
                  listPage.filter(workspaceName)
                  listPage.ui.looksRestricted(projectName, workspaceName) shouldEqual true

                  val summaryPage = listPage.openWorkspaceDetails(projectName, workspaceName).awaitLoaded()
                  summaryPage.ui.readAuthDomainGroups should include(authDomainName)
                }
              }
            }
          }
        }
        //TCGA controlled access workspaces use-case
        "when the workspace is shared with the group" - {
          "can be seen and is accessible" in withWebDriver { implicit driver =>
            withGroup("AuthDomainSpec", List(Config.Users.harry.email)) { groupOneName =>
              withGroup("AuthDomainSpec", List(Config.Users.harry.email)) { groupTwoName =>
                withWorkspace(projectName, "AuthDomainSpec_share", Set(groupOneName, groupTwoName), List(AclEntry(groupNameToEmail(groupOneName), WorkspaceAccessLevel.Reader))) { workspaceName =>
                  val listPage = signIn(Config.Users.harry)
                  listPage.filter(workspaceName)
                  listPage.ui.looksRestricted(projectName, workspaceName) shouldEqual true

                  val summaryPage = listPage.openWorkspaceDetails(projectName, workspaceName).awaitLoaded()
                  summaryPage.ui.readAuthDomainGroups should include(groupOneName)
                  summaryPage.ui.readAuthDomainGroups should include(groupTwoName)
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
        withGroup("AuthDomainSpec") { groupOneName =>
          withGroup("AuthDomainSpec") { groupTwoName =>
            withCleanUp {
              val workspaceListPage = signIn(Config.Users.fred)

              val workspaceName = "AuthDomainSpec_create_" + randomUuid
              register cleanUp api.workspaces.delete(projectName, workspaceName)
              val workspaceDetailPage = workspaceListPage.createWorkspace(projectName, workspaceName, Set(groupOneName, groupTwoName)).awaitLoaded()

              workspaceDetailPage.ui.readAuthDomainGroups should include(groupOneName)
              workspaceDetailPage.ui.readAuthDomainGroups should include(groupTwoName)

              workspaceListPage.open.filter(workspaceName)
              workspaceListPage.ui.looksRestricted(projectName, workspaceName) shouldEqual true
            }
          }
        }
      }
      "can be cloned and retain the auth domain" in withWebDriver { implicit driver =>
        withGroup("AuthDomainSpec", List(Config.Users.george.email)) { groupOneName =>
          withGroup("AuthDomainSpec", List(Config.Users.george.email)) { groupTwoName =>
            withWorkspace(projectName, "AuthDomainSpec_share", Set(groupOneName, groupTwoName)) { workspaceName =>
              withCleanUp {
                api.workspaces.updateAcl(projectName, workspaceName,
                  Config.Users.george.email, WorkspaceAccessLevel.Reader)

                val listPage = signIn(Config.Users.george)
                val summaryPage = listPage.openWorkspaceDetails(projectName, workspaceName)

                val cloneWorkspaceName = workspaceName + "_clone"
                val cloneModal = summaryPage.ui.clickCloneButton()
                cloneModal.ui.readLockedAuthDomainGroups() should contain(groupOneName)
                cloneModal.ui.readLockedAuthDomainGroups() should contain(groupTwoName)

                register cleanUp {
                  api.workspaces.delete(projectName, cloneWorkspaceName)(AuthTokens.george)
                }
                cloneModal.cloneWorkspace(projectName, cloneWorkspaceName)
                cloneModal.cloneWorkspaceWait()
                summaryPage.cloneWorkspaceWait()

                val cloneSummaryPage = new WorkspaceSummaryPage(projectName, cloneWorkspaceName).awaitLoaded()
                cloneSummaryPage.ui.readWorkspaceName should be(cloneWorkspaceName)
                cloneSummaryPage.ui.readAuthDomainGroups should include(groupOneName)
                cloneSummaryPage.ui.readAuthDomainGroups should include(groupTwoName)
              }
            }
          }
        }
      }
      "can be cloned and have a group added to the auth domain" in withWebDriver { implicit driver =>
        withGroup("AuthDomainSpec", List(Config.Users.george.email)) { groupOneName =>
          withGroup("AuthDomainSpec", List(Config.Users.george.email)) { groupTwoName =>
            withGroup("AuthDomainSpec", List(Config.Users.george.email)) { groupThreeName =>
              withWorkspace(projectName, "AuthDomainSpec_share", Set(groupOneName, groupTwoName)) { workspaceName =>
                withCleanUp {
                  api.workspaces.updateAcl(projectName, workspaceName,
                    Config.Users.george.email, WorkspaceAccessLevel.Reader)

                  val listPage = signIn(Config.Users.george)
                  val summaryPage = listPage.openWorkspaceDetails(projectName, workspaceName)

                  val cloneWorkspaceName = workspaceName + "_clone"
                  val cloneModal = summaryPage.ui.clickCloneButton()
                  cloneModal.ui.readLockedAuthDomainGroups() should contain(groupOneName)
                  cloneModal.ui.readLockedAuthDomainGroups() should contain(groupTwoName)
                  cloneModal.ui.selectAuthDomain(groupThreeName)

                  register cleanUp {
                    api.workspaces.delete(projectName, cloneWorkspaceName)(AuthTokens.george)
                  }
                  cloneModal.cloneWorkspace(projectName, cloneWorkspaceName)
                  cloneModal.cloneWorkspaceWait()
                  summaryPage.cloneWorkspaceWait()

                  val cloneSummaryPage = new WorkspaceSummaryPage(projectName, cloneWorkspaceName).awaitLoaded()
                  cloneSummaryPage.ui.readWorkspaceName should be(cloneWorkspaceName)
                  cloneSummaryPage.ui.readAuthDomainGroups should include(groupOneName)
                  cloneSummaryPage.ui.readAuthDomainGroups should include(groupTwoName)
                  cloneSummaryPage.ui.readAuthDomainGroups should include(groupThreeName)
                }
              }
            }
          }
        }
      }

      "when the user is in none of the groups" - {
        "when shared with them" - {
          "can be seen but is not accessible" in withWebDriver { implicit driver =>
            withGroup("AuthDomainSpec") { groupOneName =>
              withGroup("AuthDomainSpec") { groupTwoName =>
                withWorkspace(projectName, "AuthDomainSpec_reject", Set(groupOneName, groupTwoName)) { workspaceName =>
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
        }
        "when not shared with them" - {
          "cannot be seen and is not accessible" in withWebDriver { implicit driver =>
            withGroup("AuthDomainSpec") { groupOneName =>
              withGroup("AuthDomainSpec") { groupTwoName =>
                withWorkspace(projectName, "AuthDomainSpec", Set(groupOneName, groupTwoName)) { workspaceName =>
                  val workspaceListPage = signIn(Config.Users.ron)

                  workspaceListPage.filter(workspaceName)
                  workspaceListPage.ui.hasWorkspace(projectName, workspaceName) shouldEqual false

                  val workspaceSummaryPage = new WorkspaceSummaryPage(projectName, workspaceName)
                  go to workspaceSummaryPage
                  workspaceSummaryPage.awaitLoaded()
                  workspaceSummaryPage.ui.readError() should include(projectName)
                  workspaceSummaryPage.ui.readError() should include(workspaceName)
                  workspaceSummaryPage.ui.readError() should include("does not exist")
                }
              }
            }
          }
        }
      }

      "when the user is in one of the groups" - {
        "when shared with them" - {
          "can be seen but is not accessible" in withWebDriver { implicit driver =>
            withGroup("AuthDomainSpec") { groupOneName =>
              withGroup("AuthDomainSpec", List(Config.Users.george.email)) { groupTwoName =>
                withWorkspace(projectName, "AuthDomainSpec_reject", Set(groupOneName, groupTwoName)) { workspaceName =>
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
        }
        "when not shared with them" - {
          "cannot be seen and is not accessible" in withWebDriver { implicit driver =>
            withGroup("AuthDomainSpec") { groupOneName =>
              withGroup("AuthDomainSpec", List(Config.Users.george.email)) { groupTwoName =>
                withWorkspace(projectName, "AuthDomainSpec", Set(groupOneName, groupTwoName)) { workspaceName =>
                  val workspaceListPage = signIn(Config.Users.ron)

                  workspaceListPage.filter(workspaceName)
                  workspaceListPage.ui.hasWorkspace(projectName, workspaceName) shouldEqual false

                  val workspaceSummaryPage = new WorkspaceSummaryPage(projectName, workspaceName)
                  go to workspaceSummaryPage
                  workspaceSummaryPage.awaitLoaded()
                  workspaceSummaryPage.ui.readError() should include(projectName)
                  workspaceSummaryPage.ui.readError() should include(workspaceName)
                  workspaceSummaryPage.ui.readError() should include("does not exist")
                }
              }
            }
          }
          "when the user is a billing project owner" - {
            "can be seen but is not accessible" in withWebDriver { implicit driver =>
              withGroup("AuthDomainSpec") { groupOneName =>
                withGroup("AuthDomainSpec") { groupTwoName =>
                  withWorkspace(projectName, "AuthDomainSpec_reject", Set(groupOneName, groupTwoName)) { workspaceName =>
                    val workspaceListPage = signIn(Config.Users.hermione)
                    workspaceListPage.filter(workspaceName)
                    workspaceListPage.ui.looksRestricted(projectName, workspaceName) shouldEqual true

                    workspaceListPage.ui.clickWorkspaceInList(projectName, workspaceName)
                    // micro-sleep just long enough to let the app navigate elsewhere if it's going to, which it shouldn't in this case
                    Thread sleep 500
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
            withGroup("AuthDomainSpec", List(Config.Users.harry.email)) { groupOneName =>
              withGroup("AuthDomainSpec", List(Config.Users.harry.email)) { groupTwoName =>
                withWorkspace(projectName, "AuthDomainSpec_share", Set(groupOneName, groupTwoName), List(AclEntry(Config.Users.harry.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
                  val listPage = signIn(Config.Users.harry)
                  listPage.filter(workspaceName)
                  listPage.ui.looksRestricted(projectName, workspaceName) shouldEqual true

                  val summaryPage = listPage.openWorkspaceDetails(projectName, workspaceName).awaitLoaded()
                  summaryPage.ui.readAuthDomainGroups should include(groupOneName)
                  summaryPage.ui.readAuthDomainGroups should include(groupTwoName)
                }
              }
            }
          }
          "when the user is a billing project owner" - {
            "should be visible and accessible" in withWebDriver { implicit driver =>
              withGroup("AuthDomainSpec", List(Config.Users.harry.email)) { groupOneName =>
                withGroup("AuthDomainSpec", List(Config.Users.harry.email)) { groupTwoName =>
                  withWorkspace(projectName, "AuthDomainSpec_share", Set(groupOneName, groupTwoName), List(AclEntry(Config.Users.harry.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
                    api.billing.addUserToBillingProject(projectName, Config.Users.harry.email, BillingProjectRole.Owner)(AuthTokens.hermione)
                    register cleanUp {
                      api.billing.removeUserFromBillingProject(projectName, Config.Users.harry.email, BillingProjectRole.Owner)(AuthTokens.hermione)
                    }

                    val listPage = signIn(Config.Users.harry)
                    listPage.filter(workspaceName)
                    listPage.ui.looksRestricted(projectName, workspaceName) shouldEqual true

                    val summaryPage = listPage.openWorkspaceDetails(projectName, workspaceName).awaitLoaded()
                    summaryPage.ui.readAuthDomainGroups should include(groupOneName)
                    summaryPage.ui.readAuthDomainGroups should include(groupTwoName)
                  }
                }
              }
            }
          }
        }
        "when not shared with them" - {
          "cannot be seen and is not accessible" in withWebDriver { implicit driver =>
            withGroup("AuthDomainSpec", List(Config.Users.harry.email)) { groupOneName =>
              withGroup("AuthDomainSpec", List(Config.Users.harry.email)) { groupTwoName =>
                withWorkspace(projectName, "AuthDomainSpec_reject", Set(groupOneName, groupTwoName)) { workspaceName =>
                  val workspaceListPage = signIn(Config.Users.george)
                  workspaceListPage.filter(workspaceName)
                  workspaceListPage.ui.hasWorkspace(projectName, workspaceName) shouldEqual false

                  val workspaceSummaryPage = new WorkspaceSummaryPage(projectName, workspaceName)
                  go to workspaceSummaryPage
                  workspaceSummaryPage.awaitLoaded()
                  workspaceSummaryPage.ui.readError() should include(projectName)
                  workspaceSummaryPage.ui.readError() should include(workspaceName)
                  workspaceSummaryPage.ui.readError() should include("does not exist")
                }
              }
            }
          }
          "when the user is a billing project owner" - {
            "can be seen and is accessible" in withWebDriver { implicit driver =>
              withGroup("AuthDomainSpec", List(Config.Users.hermione.email)) { groupOneName =>
                withGroup("AuthDomainSpec", List(Config.Users.hermione.email)) { groupTwoName =>
                  withWorkspace(projectName, "AuthDomainSpec_share", Set(groupOneName, groupTwoName)) { workspaceName =>
                    val listPage = signIn(Config.Users.hermione)
                    listPage.filter(workspaceName)
                    listPage.ui.looksRestricted(projectName, workspaceName) shouldEqual true

                    val summaryPage = listPage.openWorkspaceDetails(projectName, workspaceName).awaitLoaded()
                    summaryPage.ui.readAuthDomainGroups should include(groupOneName)
                    summaryPage.ui.readAuthDomainGroups should include(groupTwoName)
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
