package org.broadinstitute.dsde.firecloud.fixture

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.api.Orchestration.groups.GroupRole
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config._
import org.broadinstitute.dsde.workbench.service.test.CleanUp
import org.broadinstitute.dsde.firecloud.test.WebBrowserSpec
import org.broadinstitute.dsde.workbench.service.util.Util
import org.scalatest.TestSuite

/**
  * Fixtures for creating and cleaning up test groups.
  */
trait GroupFixtures extends CleanUp with LazyLogging { self: WebBrowserSpec with TestSuite =>

  def groupNameToEmail(groupName: String): String = s"GROUP_$groupName@${Config.GCS.appsDomain}"

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
