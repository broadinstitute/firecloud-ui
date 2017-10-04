package org.broadinstitute.dsde.firecloud.test.security

import org.broadinstitute.dsde.firecloud.api.Orchestration.billing.BillingProjectRole
import org.broadinstitute.dsde.firecloud.api.{AclEntry, WorkspaceAccessLevel}
import org.broadinstitute.dsde.firecloud.config.{AuthToken, AuthTokens, Config}
import org.broadinstitute.dsde.firecloud.fixture.{GroupFixtures, UserFixtures, WorkspaceFixtures}
import org.broadinstitute.dsde.firecloud.page.billing.BillingManagementPage
import org.broadinstitute.dsde.firecloud.page.workspaces.summary.WorkspaceSummaryPage
import org.broadinstitute.dsde.firecloud.test.{CleanUp, WebBrowserSpec}
import org.scalatest._
import org.broadinstitute.dsde.firecloud.test.Tags


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
  with GroupFixtures
  with UserFixtures {

  val projectName: String = Config.Projects.common

  // Unless otherwise declared, this auth token will be used for API calls.
  implicit val authToken: AuthToken = AuthTokens.fred

  "A workspace with an authorization domain" - {
    "with one group inside of it" - {
      "can be created" in withWebDriver { implicit driver =>
        withGroup("AuthDomain") { authDomainName =>
          withCleanUp {
            withSignIn(Config.Users.fred) { listPage =>
              val workspaceName = "AuthDomainSpec_create_" + randomUuid
              register cleanUp api.workspaces.delete(projectName, workspaceName)
              val workspaceSummaryPage = listPage.createWorkspace(projectName, workspaceName, Set(authDomainName))

              workspaceSummaryPage.ui.readAuthDomainGroups should include(authDomainName)
            }

          }
        }
      }

      "can be cloned and retain the auth domain" in withWebDriver { implicit driver =>
        withGroup("AuthDomain", List(Config.Users.george.email)) { authDomainName =>
          withWorkspace(projectName, "AuthDomainSpec_share", Set(authDomainName), List(AclEntry(Config.Users.george.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
            withCleanUp {
              withSignIn(Config.Users.george) { listPage =>
                val summaryPage = listPage.openWorkspaceDetails(projectName, workspaceName)

                val cloneWorkspaceName = workspaceName + "_clone"
                val cloneModal = summaryPage.ui.clickCloneButton()
                cloneModal.readLockedAuthDomainGroups() should contain(authDomainName)

              register cleanUp {
                api.workspaces.delete(projectName, cloneWorkspaceName)(AuthTokens.george)
              }


              val cloneSummaryPage = cloneModal.cloneWorkspace(projectName, cloneWorkspaceName)
              cloneSummaryPage.validateWorkspace shouldEqual true
              cloneSummaryPage.ui.readAuthDomainGroups should include(authDomainName)}
            }
          }
        }
      }

      "when the user is not inside of the group" - {
        "when the workspace is shared with them" - {
          "can be seen but is not accessible" in withWebDriver { implicit driver =>
            withGroup("AuthDomain") { authDomainName =>
              withWorkspace(projectName, "AuthDomainSpec_reject", Set(authDomainName), List(AclEntry(Config.Users.draco.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
                withSignIn(Config.Users.draco) { workspaceListPage =>
                  workspaceListPage.filter(workspaceName)

                  workspaceListPage.ui.clickWorkspaceInList(projectName, workspaceName)
                  workspaceListPage.ui.showsRequestAccessModal shouldEqual true
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
            withGroup("AuthDomain") { authDomainName =>
              withWorkspace(projectName, "AuthDomainSpec", Set(authDomainName)) { workspaceName =>
                withSignIn(Config.Users.draco) { workspaceListPage =>
                  workspaceListPage.filter(workspaceName)
                  workspaceListPage.ui.hasWorkspace(projectName, workspaceName) shouldEqual false

                val workspaceSummaryPage = new WorkspaceSummaryPage(projectName, workspaceName)
                go to workspaceSummaryPage

                workspaceSummaryPage.ui.readError() should include(projectName)
                workspaceSummaryPage.ui.readError() should include(workspaceName)
                workspaceSummaryPage.ui.readError() should include("does not exist")}
              }
            }
          }
        }
      }

      "when the user is inside of the group" - {
        "when the workspace is shared with them" - {
          "can be seen and is accessible" in withWebDriver { implicit driver =>
            withGroup("AuthDomain", List(Config.Users.george.email)) { authDomainName =>
              withWorkspace(projectName, "AuthDomainSpec_share", Set(authDomainName), List(AclEntry(Config.Users.george.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
                withSignIn(Config.Users.george) { listPage =>
                  listPage.filter(workspaceName)

                  val summaryPage = listPage.openWorkspaceDetails(projectName, workspaceName)
                  summaryPage.ui.readAuthDomainGroups should include(authDomainName)
                }
              }
            }
          }
        }
        "when the workspace is not shared with them" - {
          "cannot be seen and is not accessible" in withWebDriver { implicit driver =>
            withGroup("AuthDomain", List(Config.Users.george.email)) { authDomainName =>
              withWorkspace(projectName, "AuthDomainSpec", Set(authDomainName)) { workspaceName =>
                withSignIn(Config.Users.george) { workspaceListPage =>
                  workspaceListPage.filter(workspaceName)
                  workspaceListPage.ui.hasWorkspace(projectName, workspaceName) shouldEqual false

                val workspaceSummaryPage = new WorkspaceSummaryPage(projectName, workspaceName)
                go to workspaceSummaryPage

                workspaceSummaryPage.ui.readError() should include(projectName)
                workspaceSummaryPage.ui.readError() should include(workspaceName)
                workspaceSummaryPage.ui.readError() should include("does not exist")}
              }
            }
          }
        }
        //TCGA controlled access workspaces use-case
        "when the workspace is shared with the group" - {
          "can be seen and is accessible" in withWebDriver { implicit driver =>
            withGroup("AuthDomain", List(Config.Users.draco.email)) { groupOneName =>
              withGroup("AuthDomain", List(Config.Users.draco.email)) { groupTwoName =>
                withWorkspace(projectName, "AuthDomainSpec_share", Set(groupOneName, groupTwoName), List(AclEntry(groupNameToEmail(groupOneName), WorkspaceAccessLevel.Reader))) { workspaceName =>
                  withSignIn(Config.Users.draco) { listPage =>
                    listPage.filter(workspaceName)

                    val summaryPage = listPage.openWorkspaceDetails(projectName, workspaceName)
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

    //MULTI-GROUP AUTH DOMAIN TESTS

    "with multiple groups inside of it" - {
      "can be created" in withWebDriver { implicit driver =>
        withGroup("AuthDomain") { groupOneName =>
          withGroup("AuthDomain") { groupTwoName =>
            withCleanUp {
              withSignIn(Config.Users.fred) { workspaceListPage =>
                val workspaceName = "AuthDomainSpec_create_" + randomUuid
                register cleanUp api.workspaces.delete(projectName, workspaceName)
                val workspaceSummaryPage = workspaceListPage.createWorkspace(projectName, workspaceName, Set(groupOneName, groupTwoName))

                workspaceSummaryPage.ui.readAuthDomainGroups should include(groupOneName)
                workspaceSummaryPage.ui.readAuthDomainGroups should include(groupTwoName)
              }
            }
          }
        }
      }
      "can be cloned and retain the auth domain" in withWebDriver { implicit driver =>
        withGroup("AuthDomain", List(Config.Users.george.email)) { groupOneName =>
          withGroup("AuthDomain", List(Config.Users.george.email)) { groupTwoName =>
            withWorkspace(projectName, "AuthDomainSpec_share", Set(groupOneName, groupTwoName), List(AclEntry(Config.Users.george.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
              withCleanUp {
                withSignIn(Config.Users.george) { listPage =>
                  val summaryPage = listPage.openWorkspaceDetails(projectName, workspaceName).awaitLoaded()

                  val cloneWorkspaceName = workspaceName + "_clone"
                  val cloneModal = summaryPage.ui.clickCloneButton()
                  cloneModal.readLockedAuthDomainGroups() should contain(groupOneName)
                  cloneModal.readLockedAuthDomainGroups() should contain(groupTwoName)

                register cleanUp {
                  api.workspaces.delete(projectName, cloneWorkspaceName)(AuthTokens.george)
                }


                val cloneSummaryPage = cloneModal.cloneWorkspace(projectName, cloneWorkspaceName)
                cloneSummaryPage.validateWorkspace shouldEqual true
                cloneSummaryPage.ui.readAuthDomainGroups should include(groupOneName)
                cloneSummaryPage.ui.readAuthDomainGroups should include(groupTwoName)}
              }
            }
          }
        }
      }
      "can be cloned and have a group added to the auth domain" in withWebDriver { implicit driver =>
        withGroup("AuthDomain", List(Config.Users.george.email)) { groupOneName =>
          withGroup("AuthDomain", List(Config.Users.george.email)) { groupTwoName =>
            withGroup("AuthDomain", List(Config.Users.george.email)) { groupThreeName =>
              withWorkspace(projectName, "AuthDomainSpec_share", Set(groupOneName, groupTwoName), List(AclEntry(Config.Users.george.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
                withCleanUp {
                  withSignIn(Config.Users.george) { listPage =>
                    val cloneWorkspaceName = workspaceName + "_clone"
                    val summaryPage = listPage.openWorkspaceDetails(projectName, workspaceName)

                    summaryPage.cloneWorkspace(projectName, cloneWorkspaceName, Set(groupThreeName))

                    register cleanUp {
                      api.workspaces.delete(projectName, cloneWorkspaceName)(AuthTokens.george)
                    }

                    summaryPage.ui.readAuthDomainGroups should include(groupOneName)
                    summaryPage.ui.readAuthDomainGroups should include(groupTwoName)
                    summaryPage.ui.readAuthDomainGroups should include(groupThreeName)
                  }
                }
              }
            }
          }
        }
      }
      "looks restricted in the workspace list page" in withWebDriver { implicit driver =>
        withGroup("AuthDomain") { groupOneName =>
          withGroup("AuthDomain") { groupTwoName =>
            withWorkspace(projectName, "AuthDomainSpec_create", Set(groupOneName, groupTwoName)) { workspaceName =>
              withCleanUp {
                withSignIn(Config.Users.fred) { workspaceListPage =>
                  workspaceListPage.open.filter(workspaceName)
                  workspaceListPage.ui.looksRestricted(projectName, workspaceName) shouldEqual true
                }
              }
            }
          }
        }
      }
      "contains the list of auth domain groups in the workspace summary page" in withWebDriver { implicit driver =>
        withGroup("AuthDomain") { groupOneName =>
          withGroup("AuthDomain") { groupTwoName =>
            withCleanUp {
              withSignIn(Config.Users.fred) { workspaceListPage =>
                val workspaceName = "AuthDomainSpec_create_" + randomUuid
                register cleanUp api.workspaces.delete(projectName, workspaceName)
                val workspaceSummaryPage = workspaceListPage.createWorkspace(projectName, workspaceName, Set(groupOneName, groupTwoName))

                workspaceSummaryPage.ui.readAuthDomainGroups should include(groupOneName)
                workspaceSummaryPage.ui.readAuthDomainGroups should include(groupTwoName)
              }
            }
          }
        }
      }

      "when the user is in none of the groups" - {
        "when shared with them" - {
          "can be seen but is not accessible" in withWebDriver { implicit driver =>
            withGroup("AuthDomain") { groupOneName =>
              withGroup("AuthDomain") { groupTwoName =>
                withWorkspace(projectName, "AuthDomainSpec_reject", Set(groupOneName, groupTwoName), List(AclEntry(Config.Users.draco.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
                  withSignIn(Config.Users.draco) { workspaceListPage =>
                    workspaceListPage.filter(workspaceName)

                    workspaceListPage.ui.clickWorkspaceInList(projectName, workspaceName)
                    workspaceListPage.ui.showsRequestAccessModal shouldEqual true
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
            withGroup("AuthDomain") { groupOneName =>
              withGroup("AuthDomain") { groupTwoName =>
                withWorkspace(projectName, "AuthDomainSpec", Set(groupOneName, groupTwoName)) { workspaceName =>
                  withSignIn(Config.Users.draco) { workspaceListPage =>
                    workspaceListPage.filter(workspaceName)
                    workspaceListPage.ui.hasWorkspace(projectName, workspaceName) shouldEqual false

                  val workspaceSummaryPage = new WorkspaceSummaryPage(projectName, workspaceName)
                  go to workspaceSummaryPage

                  workspaceSummaryPage.ui.readError() should include(projectName)
                  workspaceSummaryPage.ui.readError() should include(workspaceName)
                  workspaceSummaryPage.ui.readError() should include("does not exist")}
                }
              }
            }
          }
        }
      }

      "when the user is in one of the groups" - {
        "when shared with them" - {
          "can be seen but is not accessible" in withWebDriver { implicit driver =>
            withGroup("AuthDomain") { groupOneName =>
              withGroup("AuthDomain", List(Config.Users.draco.email)) { groupTwoName =>
                withWorkspace(projectName, "AuthDomainSpec_reject", Set(groupOneName, groupTwoName), List(AclEntry(Config.Users.draco.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
                  withSignIn(Config.Users.draco) { workspaceListPage =>
                    workspaceListPage.filter(workspaceName)

                    workspaceListPage.ui.clickWorkspaceInList(projectName, workspaceName)
                    workspaceListPage.ui.showsRequestAccessModal shouldEqual true
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
              withGroup("AuthDomain") { authDomainName =>
                withWorkspace(projectName, "AuthDomainSpec_reject", Set(authDomainName)) { workspaceName =>
                  withSignIn(Config.Users.hermione) { workspaceListPage =>
                    workspaceListPage.filter(workspaceName)

                    workspaceListPage.ui.clickWorkspaceInList(projectName, workspaceName)
                    workspaceListPage.ui.showsRequestAccessModal shouldEqual true
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
            withGroup("AuthDomain") { groupOneName =>
              withGroup("AuthDomain", List(Config.Users.draco.email)) { groupTwoName =>
                withWorkspace(projectName, "AuthDomainSpec", Set(groupOneName, groupTwoName)) { workspaceName =>
                  withSignIn(Config.Users.draco) { workspaceListPage =>
                    workspaceListPage.filter(workspaceName)
                    workspaceListPage.ui.hasWorkspace(projectName, workspaceName) shouldEqual false

                  val workspaceSummaryPage = new WorkspaceSummaryPage(projectName, workspaceName)
                  go to workspaceSummaryPage

                  workspaceSummaryPage.ui.readError() should include(projectName)
                  workspaceSummaryPage.ui.readError() should include(workspaceName)
                  workspaceSummaryPage.ui.readError() should include("does not exist")}
                }
              }
            }
          }
          "when the user is a billing project owner" - {
            "can be seen but is not accessible" in withWebDriver { implicit driver =>
              withGroup("AuthDomain") { authDomainName =>
                withWorkspace(projectName, "AuthDomainSpec_reject", Set(authDomainName)) { workspaceName =>
                  withSignIn(Config.Users.hermione) { workspaceListPage =>
                    workspaceListPage.filter(workspaceName)

                    workspaceListPage.ui.clickWorkspaceInList(projectName, workspaceName)
                    workspaceListPage.ui.showsRequestAccessModal shouldEqual true
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
            withGroup("AuthDomain", List(Config.Users.draco.email)) { groupOneName =>
              withGroup("AuthDomain", List(Config.Users.draco.email)) { groupTwoName =>
                withWorkspace(projectName, "AuthDomainSpec_share", Set(groupOneName, groupTwoName), List(AclEntry(Config.Users.draco.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
                  withSignIn(Config.Users.draco) { listPage =>
                    listPage.filter(workspaceName)

                    val summaryPage = listPage.openWorkspaceDetails(projectName, workspaceName)
                    summaryPage.ui.readAuthDomainGroups should include(groupOneName)
                    summaryPage.ui.readAuthDomainGroups should include(groupTwoName)
                  }
                }
              }
            }
          }
          "and given writer access" - {
            "the user has correct permissions" in withWebDriver { implicit driver =>
              withGroup("AuthDomain", List(Config.Users.draco.email)) { groupOneName =>
                withGroup("AuthDomain", List(Config.Users.draco.email)) { groupTwoName =>
                  withWorkspace(projectName, "AuthDomainSpec_create", Set(groupOneName, groupTwoName), List(AclEntry(Config.Users.draco.email, WorkspaceAccessLevel.Writer))) { workspaceName =>
                    withCleanUp {
                      withSignIn(Config.Users.draco) { workspaceListPage =>
                        workspaceListPage.filter(workspaceName)

                        val summaryPage = workspaceListPage.openWorkspaceDetails(projectName, workspaceName)
                        summaryPage.ui.readAccessLevel() should be(WorkspaceAccessLevel.Writer)
                      }
                    }
                  }
                }
              }
            }
          }
          "when the user is a billing project owner" - {
            "can be seen and is accessible" in withWebDriver { implicit driver =>
              withGroup("AuthDomain", List(Config.Users.hermione.email)) { groupOneName =>
                withGroup("AuthDomain", List(Config.Users.hermione.email)) { groupTwoName =>
                  withWorkspace(projectName, "AuthDomainSpec_share", Set(groupOneName, groupTwoName)) { workspaceName =>
                    withSignIn(Config.Users.hermione) { listPage =>
                      listPage.filter(workspaceName)

                      val summaryPage = listPage.openWorkspaceDetails(projectName, workspaceName)
                      summaryPage.ui.readAuthDomainGroups should include(groupOneName)
                      summaryPage.ui.readAuthDomainGroups should include(groupTwoName)
                    }
                  }
                }
              }
            }
          }
        }
        "when shared with one of the groups in the auth domain" - {
          "can be seen and is accessible by group member who is a member of both auth domain groups" in withWebDriver { implicit driver =>
            withGroup("AuthDomain", List(Config.Users.draco.email)) { groupOneName =>
              withGroup("AuthDomain", List(Config.Users.draco.email)) { groupTwoName =>
                withWorkspace(projectName, "AuthDomainSpec_share", Set(groupOneName, groupTwoName), List(AclEntry(groupNameToEmail(groupOneName), WorkspaceAccessLevel.Reader))) { workspaceName =>
                  withSignIn(Config.Users.draco) { listPage =>
                    listPage.filter(workspaceName)

                    val summaryPage = listPage.openWorkspaceDetails(projectName, workspaceName)
                    summaryPage.ui.readAuthDomainGroups should include(groupOneName)
                    summaryPage.ui.readAuthDomainGroups should include(groupTwoName)
                  }
                }
              }
            }
          }
          "can be seen but is not accessible by group member who is a member of only one auth domain group"in withWebDriver { implicit driver =>
            withGroup("AuthDomain", List(Config.Users.draco.email)) { groupOneName =>
              withGroup("AuthDomain") { groupTwoName =>
                withWorkspace(projectName, "AuthDomainSpec_share", Set(groupOneName, groupTwoName), List(AclEntry(groupNameToEmail(groupOneName), WorkspaceAccessLevel.Reader))) { workspaceName =>
                  withSignIn(Config.Users.draco) { workspaceListPage =>
                    workspaceListPage.filter(workspaceName)
                    workspaceListPage.ui.hasWorkspace(projectName, workspaceName) shouldEqual true

                  val workspaceSummaryPage = new WorkspaceSummaryPage(projectName, workspaceName)
                  go to workspaceSummaryPage

                  workspaceSummaryPage.ui.readError() should include(projectName)
                  workspaceSummaryPage.ui.readError() should include(workspaceName)
                  workspaceSummaryPage.ui.readError() should include("does not exist")}
                }
              }
            }
          }
        }
        "when not shared with them" - {
          "cannot be seen and is not accessible" in withWebDriver { implicit driver =>
            withGroup("AuthDomain", List(Config.Users.draco.email)) { groupOneName =>
              withGroup("AuthDomain", List(Config.Users.draco.email)) { groupTwoName =>
                withWorkspace(projectName, "AuthDomainSpec_reject", Set(groupOneName, groupTwoName)) { workspaceName =>
                  withSignIn(Config.Users.draco) { workspaceListPage =>
                    workspaceListPage.filter(workspaceName)
                    workspaceListPage.ui.hasWorkspace(projectName, workspaceName) shouldEqual false

                  val workspaceSummaryPage = new WorkspaceSummaryPage(projectName, workspaceName)
                  go to workspaceSummaryPage

                  workspaceSummaryPage.ui.readError() should include(projectName)
                  workspaceSummaryPage.ui.readError() should include(workspaceName)
                  workspaceSummaryPage.ui.readError() should include("does not exist")}
                }
              }
            }
          }
        }
      }
    }
  }
}
