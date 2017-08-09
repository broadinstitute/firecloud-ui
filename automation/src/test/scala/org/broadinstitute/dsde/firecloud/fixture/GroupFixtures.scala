package org.broadinstitute.dsde.firecloud.fixture

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.api.Orchestration.groups.GroupRole
import org.broadinstitute.dsde.firecloud.config.AuthToken
import org.broadinstitute.dsde.firecloud.test.{CleanUp, WebBrowserSpec}
import org.broadinstitute.dsde.firecloud.util.Util
import org.scalatest.Suite

/**
  * Fixtures for creating and cleaning up test groups.
  */
trait GroupFixtures extends CleanUp with LazyLogging { self: WebBrowserSpec with Suite =>

  def groupNameToEmail(groupName: String): String = s"GROUP_$groupName@quality.firecloud.org" //probably want to pull the apps domain from config if it's there

  def withGroup(namePrefix: String, memberEmails: List[String] = List())
               (testCode: (String) => Any)
               (implicit token: AuthToken): Unit = {
    val groupName = Util.appendUnderscore(namePrefix) + Util.makeUuid

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
