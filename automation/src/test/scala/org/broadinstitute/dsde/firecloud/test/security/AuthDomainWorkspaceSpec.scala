package org.broadinstitute.dsde.firecloud.test.security

import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.firecloud.page.workspaces.RequestAccessModal
import org.broadinstitute.dsde.firecloud.page.workspaces.summary.WorkspaceSummaryPage
import org.broadinstitute.dsde.firecloud.test.Tags
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{Credentials, UserPool}
import org.broadinstitute.dsde.workbench.fixture.{BillingFixtures, GroupFixtures, WorkspaceFixtures}
import org.broadinstitute.dsde.workbench.service.{AclEntry, Orchestration, WorkspaceAccessLevel}
import org.broadinstitute.dsde.workbench.service.Orchestration.billing.BillingProjectRole
import org.broadinstitute.dsde.workbench.service.test.{CleanUp, WebBrowserSpec}
import org.scalatest._
import org.scalatest.time.{Millis, Seconds, Span}


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
class AuthDomainWorkspaceSpec extends FreeSpec /*with ParallelTestExecution*/ with Matchers
  with CleanUp with WebBrowserSpec with WorkspaceFixtures
  with BillingFixtures with GroupFixtures
  with UserFixtures {

  /*
   * Unless otherwise declared, this auth token will be used for API calls.
   * We are using a curator to prevent collisions with users in tests (who are Students and AuthDomainUsers), not
   *  because we specifically need a curator.
   */
  val defaultUser: Credentials = UserPool.chooseCurator
  val authTokenDefault: AuthToken = defaultUser.makeAuthToken()

  implicit override val patienceConfig =
    PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(500, Millis)))

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
            withCleanBillingProject(user) { projectName =>
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

      "can be cloned and retain the auth domain" taggedAs Tags.SmokeTest in withWebDriver { implicit driver =>
        val user = UserPool.chooseAuthDomainUser
        implicit val authToken: AuthToken = authTokenDefault
        withGroup("AuthDomain", List(user.email)) { authDomainName =>
          withCleanBillingProject(defaultUser) { projectName =>
            Orchestration.billing.addUserToBillingProject(projectName, user.email, BillingProjectRole.User)
            withWorkspace(projectName, "AuthDomainSpec_share", Set(authDomainName), List(AclEntry(user.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
              withCleanUp {
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

      "when the user is not inside of the group" - {
        "when the workspace is shared with them" - {
          "can be seen but is not accessible" in withWebDriver { implicit driver =>
            val user = UserPool.chooseStudent
            implicit val authToken: AuthToken = authTokenDefault
            withGroup("AuthDomain") { authDomainName =>
              withCleanBillingProject(defaultUser) { projectName =>
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
        }
        "when the workspace is not shared with them" - {
          "cannot be seen and is not accessible" in withWebDriver { implicit driver =>
            val user = UserPool.chooseStudent
            implicit val authToken: AuthToken = authTokenDefault
            withGroup("AuthDomain") { authDomainName =>
              withCleanBillingProject(defaultUser) { projectName =>
                withWorkspace(projectName, "AuthDomainSpec", Set(authDomainName)) { workspaceName =>
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

      "when the user is inside of the group" - {
        "when the workspace is shared with them" - {
          "can be seen and is accessible" in withWebDriver { implicit driver =>

            val user = UserPool.chooseAuthDomainUser
            implicit val authToken: AuthToken = authTokenDefault
            withGroup("AuthDomain", List(user.email)) { authDomainName =>
              withCleanBillingProject(defaultUser) { projectName =>
                withWorkspace(projectName, "AuthDomainSpec_share", Set(authDomainName), List(AclEntry(user.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
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
        "when the workspace is not shared with them" - {
          "cannot be seen and is not accessible" in withWebDriver { implicit driver =>
            val user = UserPool.chooseAuthDomainUser
            implicit val authToken: AuthToken = authTokenDefault
            withGroup("AuthDomain", List(user.email)) { authDomainName =>
              withCleanBillingProject(defaultUser) { projectName =>
                withWorkspace(projectName, "AuthDomainSpec", Set(authDomainName)) { workspaceName =>
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
        //TCGA controlled access workspaces use-case
        "when the workspace is shared with the group" - {
          "can be seen and is accessible" in withWebDriver { implicit driver =>
            val user = UserPool.chooseAuthDomainUser
            implicit val authToken: AuthToken = authTokenDefault
            withGroup("AuthDomain", List(user.email)) { groupOneName =>
              withGroup("AuthDomain", List(user.email)) { groupTwoName =>
                withCleanBillingProject(defaultUser) { projectName =>
                  withWorkspace(projectName, "AuthDomainSpec_share", Set(groupOneName, groupTwoName), List(AclEntry(groupNameToEmail(groupOneName), WorkspaceAccessLevel.Reader))) { workspaceName =>
                    withSignIn(user) { listPage =>
                      val summaryPage = listPage.enterWorkspace(projectName, workspaceName)
                      eventually {
                        summaryPage.readAuthDomainGroups should include(groupOneName)
                      }
                      eventually {
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

    //MULTI-GROUP AUTH DOMAIN TESTS

    "with multiple groups inside of it" - {
      "can be created" in withWebDriver { implicit driver =>
        val user = UserPool.chooseAuthDomainUser
        implicit val authToken: AuthToken = authTokenDefault
        withGroup("AuthDomain", List(user.email)) { groupOneName =>
          withGroup("AuthDomain", List(user.email)) { groupTwoName =>
            withCleanUp {
              withCleanBillingProject(user) { projectName =>
                withSignIn(user) { workspaceListPage =>
                  val workspaceName = "AuthDomainSpec_create_" + randomUuid
                  register cleanUp api.workspaces.delete(projectName, workspaceName)(user.makeAuthToken())
                  val workspaceSummaryPage = workspaceListPage.createWorkspace(projectName, workspaceName, Set(groupOneName, groupTwoName))

                  eventually {
                    workspaceSummaryPage.readAuthDomainGroups should include(groupOneName)
                  }
                  eventually {
                    workspaceSummaryPage.readAuthDomainGroups should include(groupTwoName)
                  }
                }
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
            withCleanBillingProject(defaultUser) { projectName =>
              Orchestration.billing.addUserToBillingProject(projectName, user.email, BillingProjectRole.User)
              withWorkspace(projectName, "AuthDomainSpec_share", Set(groupOneName, groupTwoName), List(AclEntry(user.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
                withCleanUp {
                  withSignIn(user) { listPage =>
                    val summaryPage = listPage.enterWorkspace(projectName, workspaceName)
                    val cloneWorkspaceName = workspaceName + "_clone"
                    val cloneModal = summaryPage.clickCloneButton()
                    eventually {
                      cloneModal.readLockedAuthDomainGroups() should contain(groupOneName)
                    }
                    eventually {
                      cloneModal.readLockedAuthDomainGroups() should contain(groupTwoName)
                    }

                    register cleanUp api.workspaces.delete(projectName, cloneWorkspaceName)(authTokenDefault)

                    val cloneSummaryPage = cloneModal.cloneWorkspace(projectName, cloneWorkspaceName)
                    eventually {
                      cloneSummaryPage.validateWorkspace shouldEqual true
                    }
                    eventually {
                      cloneSummaryPage.readAuthDomainGroups should include(groupOneName)
                    }
                    eventually {
                      cloneSummaryPage.readAuthDomainGroups should include(groupTwoName)
                    }
                  }
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
              withCleanBillingProject(defaultUser) { projectName =>
                Orchestration.billing.addUserToBillingProject(projectName, user.email, BillingProjectRole.User)
                withWorkspace(projectName, "AuthDomainSpec_share", Set(groupOneName, groupTwoName), List(AclEntry(user.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
                  withCleanUp {
                    withSignIn(user) { listPage =>
                      val cloneWorkspaceName = workspaceName + "_clone"
                      val summaryPage = listPage.enterWorkspace(projectName, workspaceName)

                      summaryPage.cloneWorkspace(projectName, cloneWorkspaceName, Set(groupThreeName))

                      register cleanUp api.workspaces.delete(projectName, cloneWorkspaceName)(authTokenDefault)

                      eventually {
                        summaryPage.readAuthDomainGroups should include(groupOneName)
                      }
                      eventually {
                        summaryPage.readAuthDomainGroups should include(groupTwoName)
                      }
                      eventually {
                        summaryPage.readAuthDomainGroups should include(groupThreeName)
                      }
                    }
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
            withCleanBillingProject(user) { projectName =>
              withWorkspace(projectName, "AuthDomainSpec_create", Set(groupOneName, groupTwoName)) { workspaceName =>
                withCleanUp {
                  withSignIn(user) { workspaceListPage =>
                    eventually {
                      workspaceListPage.looksRestricted(projectName, workspaceName) shouldEqual true
                    }
                  }
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
              withCleanBillingProject(user) { projectName =>
                withSignIn(user) { workspaceListPage =>
                  val workspaceName = "AuthDomainSpec_create_" + randomUuid
                  register cleanUp api.workspaces.delete(projectName, workspaceName)(user.makeAuthToken())
                  val workspaceSummaryPage = workspaceListPage.createWorkspace(projectName, workspaceName, Set(groupOneName, groupTwoName))

                  eventually {
                    workspaceSummaryPage.readAuthDomainGroups should include(groupOneName)
                  }
                  eventually {
                    workspaceSummaryPage.readAuthDomainGroups should include(groupTwoName)
                  }
                }
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
                withCleanBillingProject(defaultUser) { projectName =>
                  withWorkspace(projectName, "AuthDomainSpec_reject", Set(groupOneName, groupTwoName), List(AclEntry(user.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
                    withSignIn(user) { workspaceListPage =>
                      workspaceListPage.clickWorkspaceLink(projectName, workspaceName)
                      eventually {
                        workspaceListPage.showsRequestAccessModal shouldEqual true
                      }
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
        }
        "when not shared with them" - {
          "cannot be seen and is not accessible" in withWebDriver { implicit driver =>
            val user = UserPool.chooseStudent
            implicit val authToken: AuthToken = authTokenDefault
            withGroup("AuthDomain") { groupOneName =>
              withGroup("AuthDomain") { groupTwoName =>
                withCleanBillingProject(defaultUser) { projectName =>
                  withWorkspace(projectName, "AuthDomainSpec", Set(groupOneName, groupTwoName)) { workspaceName =>
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

      "when the user is in one of the groups" - {
        "when shared with them" - {
          "can be seen but is not accessible" in withWebDriver { implicit driver =>
            val user = UserPool.chooseStudent
            implicit val authToken: AuthToken = authTokenDefault
            withGroup("AuthDomain") { groupOneName =>
              withGroup("AuthDomain", List(user.email)) { groupTwoName =>
                withCleanBillingProject(defaultUser) { projectName =>
                  withWorkspace(projectName, "AuthDomainSpec_reject", Set(groupOneName, groupTwoName), List(AclEntry(user.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
                    withSignIn(user) { workspaceListPage =>
                      workspaceListPage.clickWorkspaceLink(projectName, workspaceName)
                      eventually {
                        workspaceListPage.showsRequestAccessModal shouldEqual true
                      }
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
          "when the user is a billing project owner" - {
            "can be seen but is not accessible" in withWebDriver { implicit driver =>
              val user = UserPool.chooseProjectOwner
              implicit val authToken: AuthToken = authTokenDefault
              withGroup("AuthDomain") { authDomainName =>
                withCleanBillingProject(defaultUser, List(user.email)) { projectName =>
                  withWorkspace(projectName, "AuthDomainSpec_reject", Set(authDomainName)) { workspaceName =>
                    withSignIn(defaultUser) { workspaceListPage =>
                      workspaceListPage.clickWorkspaceLink(projectName, workspaceName)
                      eventually {
                        workspaceListPage.showsRequestAccessModal shouldEqual true
                      }
                      workspaceListPage.validateLocation()
                      // TODO: finish this test somewhere we can access the sign out button

                      // close "request access" Modal
                      workspaceListPage.closeModal()
                    }
                  }(user.makeAuthToken())
                }
              }(user.makeAuthToken())
            }
          }
        }
        "when not shared with them" - {
          "cannot be seen and is not accessible" in withWebDriver { implicit driver =>
            val user = UserPool.chooseStudent
            implicit val authToken: AuthToken = authTokenDefault
            withGroup("AuthDomain") { groupOneName =>
              withGroup("AuthDomain", List(user.email)) { groupTwoName =>
                withCleanBillingProject(defaultUser) { projectName =>
                  withWorkspace(projectName, "AuthDomainSpec", Set(groupOneName, groupTwoName)) { workspaceName =>
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
          "when the user is a billing project owner" - {
            "can be seen but is not accessible" in withWebDriver { implicit driver =>
              val user = UserPool.chooseProjectOwner
              implicit val authToken: AuthToken = authTokenDefault
              withGroup("AuthDomain") { authDomainName =>
                withCleanBillingProject(defaultUser, List(user.email)) { projectName =>
                  withWorkspace(projectName, "AuthDomainSpec_reject", Set(authDomainName)) { workspaceName =>
                    withSignIn(defaultUser) { workspaceListPage =>
                      workspaceListPage.clickWorkspaceLink(projectName, workspaceName)
                      eventually {
                        workspaceListPage.showsRequestAccessModal shouldEqual true
                      }
                      workspaceListPage.validateLocation()
                      // close "request access" Modal
                      workspaceListPage.closeModal()
                    }
                  }(user.makeAuthToken())
                }
              }(user.makeAuthToken())
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
                withCleanBillingProject(defaultUser) { projectName =>
                  withWorkspace(projectName, "AuthDomainSpec_share", Set(groupOneName, groupTwoName), List(AclEntry(user.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
                    withSignIn(user) { listPage =>
                      val summaryPage = listPage.enterWorkspace(projectName, workspaceName)
                      eventually {
                        summaryPage.readAuthDomainGroups should include(groupOneName)
                      }
                      eventually {
                        summaryPage.readAuthDomainGroups should include(groupTwoName)
                      }
                    }
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
                  withCleanBillingProject(defaultUser) { projectName =>
                    withWorkspace(projectName, "AuthDomainSpec_create", Set(groupOneName, groupTwoName), List(AclEntry(user.email, WorkspaceAccessLevel.Writer))) { workspaceName =>
                      withCleanUp {
                        withSignIn(user) { workspaceListPage =>
                          val summaryPage = workspaceListPage.enterWorkspace(projectName, workspaceName)
                          eventually {
                            summaryPage.readAccessLevel() should be(WorkspaceAccessLevel.Writer)
                          }
                        }
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
              implicit val authToken: AuthToken = user.makeAuthToken()
              withGroup("AuthDomain", List(user.email)) { groupOneName =>
                withGroup("AuthDomain", List(user.email)) { groupTwoName =>
                  withCleanBillingProject(user) { projectName =>
                    withWorkspace(projectName, "AuthDomainSpec_share", Set(groupOneName, groupTwoName)) { workspaceName =>
                      withSignIn(user) { listPage =>
                        val summaryPage = listPage.enterWorkspace(projectName, workspaceName)
                        eventually {
                          summaryPage.readAuthDomainGroups should include(groupOneName)
                        }
                        eventually {
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
        "when shared with one of the groups in the auth domain" - {
          "can be seen and is accessible by group member who is a member of both auth domain groups" in withWebDriver { implicit driver =>
            val user = UserPool.chooseStudent
            implicit val authToken: AuthToken = authTokenDefault
            withGroup("AuthDomain", List(user.email)) { groupOneName =>
              withGroup("AuthDomain", List(user.email)) { groupTwoName =>
                withCleanBillingProject(defaultUser) { projectName =>
                  withWorkspace(projectName, "AuthDomainSpec_share", Set(groupOneName, groupTwoName), List(AclEntry(groupNameToEmail(groupOneName), WorkspaceAccessLevel.Reader))) { workspaceName =>
                    withSignIn(user) { listPage =>
                      val summaryPage = listPage.enterWorkspace(projectName, workspaceName)
                      eventually {
                        summaryPage.readAuthDomainGroups should include(groupOneName)
                      }
                      eventually {
                        summaryPage.readAuthDomainGroups should include(groupTwoName)
                      }
                    }
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
                withCleanBillingProject(defaultUser) { projectName =>
                  withWorkspace(projectName, "AuthDomainSpec_share", Set(groupOneName, groupTwoName), List(AclEntry(groupNameToEmail(groupOneName), WorkspaceAccessLevel.Reader))) { workspaceName =>
                    withSignIn(user) { workspaceListPage =>
                      eventually {
                        workspaceListPage.hasWorkspace(projectName, workspaceName) shouldEqual true}

                      val workspaceSummaryPage = new WorkspaceSummaryPage(projectName, workspaceName).open
                      checkWorkspaceFailure(workspaceSummaryPage, projectName, workspaceName)
                    }
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
                withCleanBillingProject(defaultUser) { projectName =>
                  withWorkspace(projectName, "AuthDomainSpec_reject", Set(groupOneName, groupTwoName)) { workspaceName =>
                    withSignIn(user) { workspaceListPage =>
                      eventually {
                        workspaceListPage.hasWorkspace(projectName, workspaceName) shouldEqual false}

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


