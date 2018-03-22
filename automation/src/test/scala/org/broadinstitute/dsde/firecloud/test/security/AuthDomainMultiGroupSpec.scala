package org.broadinstitute.dsde.firecloud.test.security

import org.broadinstitute.dsde.firecloud.page.workspaces.summary.WorkspaceSummaryPage
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.UserPool
import org.broadinstitute.dsde.workbench.service.{AclEntry, WorkspaceAccessLevel}

class AuthDomainMultiGroupSpec extends AuthDomainSpecBase {


  //MULTI-GROUP AUTH DOMAIN TESTS
  "A workspace with an authorization domain" - {
    "with multiple groups inside of it" - {
      "can be created" in withWebDriver { implicit driver =>
        val user = UserPool.chooseAuthDomainUser
        implicit val authToken: AuthToken = authTokenDefault
        withGroup("AuthDomain", List(user.email)) { groupOneName =>
          withGroup("AuthDomain", List(user.email)) { groupTwoName =>
            withCleanUp {
              withSignIn(user) { workspaceListPage =>
                val workspaceName = "AuthDomainSpec_create_" + randomUuid
                register cleanUp api.workspaces.delete(projectName, workspaceName)(user.makeAuthToken())
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

                  register cleanUp api.workspaces.delete(projectName, cloneWorkspaceName)(user.makeAuthToken())

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

                    register cleanUp api.workspaces.delete(projectName, cloneWorkspaceName)(user.makeAuthToken())

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
                register cleanUp api.workspaces.delete(projectName, workspaceName)(user.makeAuthToken())
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

                    // close "request access" Modal
                    workspaceListPage.closeModal()
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

                    // close "request access" Modal
                    workspaceListPage.closeModal()
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

                    // close "request access" Modal
                    workspaceListPage.closeModal()
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
                    // close "request access" Modal
                    workspaceListPage.closeModal()
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

}
