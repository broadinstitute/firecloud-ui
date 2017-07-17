package org.broadinstitute.dsde.firecloud.fixture

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.{CleanUp, Util}
import org.broadinstitute.dsde.firecloud.api.Orchestration.groups.GroupRole
import org.broadinstitute.dsde.firecloud.auth.AuthToken
import org.broadinstitute.dsde.firecloud.pages.WebBrowserSpec
import org.scalatest.Suite

/**
  * Fixtures for creating and cleaning up test groups.
  */
trait GroupFixtures extends CleanUp with LazyLogging { self: WebBrowserSpec with Suite =>

  def withGroup(namePrefix: String = "", memberEmails: List[String] = List())
               (testCode: (String) => Any)
               (implicit token: AuthToken): Unit = {
    val groupName = (namePrefix match {
      case "" => ""
      case s => s + "_"
    }) + Util.makeUuid

    try {
      api.groups.create(groupName)
      memberEmails foreach { email =>
        api.groups.addUserToGroup(groupName, email, GroupRole.Member)
      }

      testCode(groupName)

    } finally {
      memberEmails foreach { email =>
        try api.groups.removeUserFromGroup(groupName, email, GroupRole.Member) catch nonFatalAndLog("Error removing user from group in withGroup clean-up")
      }
      try api.groups.delete(groupName) catch nonFatalAndLog("Error deleting group in withGroup clean-up")
    }
  }
}
