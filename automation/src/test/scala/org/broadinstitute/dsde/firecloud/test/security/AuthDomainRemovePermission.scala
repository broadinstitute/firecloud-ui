package org.broadinstitute.dsde.firecloud.test.security

import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{Config, UserPool}
import org.broadinstitute.dsde.workbench.service.Orchestration.billing.BillingProjectRole
import org.broadinstitute.dsde.workbench.service.Orchestration.groups.GroupRole
import org.broadinstitute.dsde.workbench.service.RestException

import scala.util.Try

class AuthDomainRemovePermission extends AuthDomainSpecBase {


  "removing permissions from billing project owners for workspaces with auth domains" - {
    "+ project owner, + group member, create workspace, - group member" in {
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

    "+ project owner, + group member, create workspace, - project owner" in {
      val owner = UserPool.chooseProjectOwner
      val creator = owner
      val user = UserPool.chooseStudent

      implicit val token: AuthToken = creator.makeAuthToken()

      withCleanBillingProject(creator) { projectName =>
        withCleanUp {
          api.billing.addUserToBillingProject(projectName, user.email, BillingProjectRole.Owner)
          register cleanUp Try(api.billing.removeUserFromBillingProject(projectName, user.email, BillingProjectRole.Owner)).recover {
            case _: RestException =>
          }

          withGroup("AuthDomain", List(user.email)) { groupName =>
            withWorkspace(projectName, "AuthDomainSpec_revoke", Set(groupName)) { workspaceName =>
              checkVisibleAndAccessible(user, projectName, workspaceName)

              api.billing.removeUserFromBillingProject(projectName, user.email, BillingProjectRole.Owner)
              checkNoAccess(user, projectName, workspaceName)
            }
          }
        }
      }
    }

    "+ project owner, create workspace, + group member, - group member" in {
      val owner = UserPool.chooseProjectOwner
      val creator = UserPool.chooseStudent
      val user = owner

      implicit val token: AuthToken = creator.makeAuthToken()

      val projectName: String = Config.Projects.default
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

    "+ project owner, create workspace, + group member, - project owner" in {
      val owner = UserPool.chooseProjectOwner
      val creator = owner
      val user = UserPool.chooseStudent

      implicit val token: AuthToken = creator.makeAuthToken()

      withCleanBillingProject(creator) { projectName =>
        withCleanUp {
          api.billing.addUserToBillingProject(projectName, user.email, BillingProjectRole.Owner)
          register cleanUp Try(api.billing.removeUserFromBillingProject(projectName, user.email, BillingProjectRole.Owner)).recover {
            case _: RestException =>
          }

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

      withCleanBillingProject(creator) { projectName =>
        withGroup("AuthDomain") { groupName =>
          withCleanUp {
            withWorkspace(projectName, "AuthDomainSpec_revoke", Set(groupName)) { workspaceName =>
              api.billing.addUserToBillingProject(projectName, user.email, BillingProjectRole.Owner)
              register cleanUp api.billing.removeUserFromBillingProject(projectName, user.email, BillingProjectRole.Owner)
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

      withCleanBillingProject(creator) { projectName =>
        withGroup("AuthDomain") { groupName =>
          withCleanUp {
            withWorkspace(projectName, "AuthDomainSpec_reject", Set(groupName)) { workspaceName =>
              api.billing.addUserToBillingProject(projectName, user.email, BillingProjectRole.Owner)
              register cleanUp Try(api.billing.removeUserFromBillingProject(projectName, user.email, BillingProjectRole.Owner)).recover {
                case _: RestException =>
              }
              checkVisibleNotAccessible(user, projectName, workspaceName)

              api.groups.addUserToGroup(groupName, user.email, GroupRole.Member)
              checkVisibleAndAccessible(user, projectName, workspaceName)

              api.billing.removeUserFromBillingProject(projectName, user.email, BillingProjectRole.Owner)
              checkNoAccess(user, projectName, workspaceName)
            }
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
        withGroup("AuthDomain") { groupName =>
          withCleanUp {
            withWorkspace(projectName, "AuthDomainSpec_reject", Set(groupName)) { workspaceName =>
              api.groups.addUserToGroup(groupName, user.email, GroupRole.Member)
              register cleanUp Try(api.groups.removeUserFromGroup(groupName, user.email, GroupRole.Member)).recover {
                case _: RestException =>
              }
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

    "create workspace, + group member, + project owner, - project owner" in {
      val owner = UserPool.chooseProjectOwner
      val creator = owner
      val user = UserPool.chooseStudent

      implicit val token: AuthToken = creator.makeAuthToken()

      withCleanBillingProject(creator) { projectName =>
        withGroup("AuthDomain") { groupName =>
          withCleanUp {
            withWorkspace(projectName, "AuthDomainSpec_reject", Set(groupName)) { workspaceName =>
              api.groups.addUserToGroup(groupName, user.email, GroupRole.Member)
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

}
