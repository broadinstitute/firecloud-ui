package org.broadinstitute.dsde.firecloud.fixture

import org.broadinstitute.dsde.firecloud.api.AclEntry
import org.broadinstitute.dsde.firecloud.auth.AuthToken
import org.broadinstitute.dsde.firecloud.test.{CleanUp, WebBrowserSpec}
import org.broadinstitute.dsde.firecloud.util.Util.{appendUnderscore, makeUuid}
import org.scalatest.TestSuite

/**WorkspaceFixtures
  * Fixtures for creating and cleaning up test workspaces.
  */
trait WorkspaceFixtures extends CleanUp { self: WebBrowserSpec with TestSuite =>

  /**
    * Loan method that creates a workspace that will be cleaned up after the
    * test code is run. The workspace name will contain a random and highly
    * likely unique series of characters.
    *
    * @param namespace the namespace for the test workspace
    * @param namePrefix optional prefix for the workspace name
    * @param authDomain optional auth domain for the test workspace
    * @param testCode test code to run
    * @param token auth token for service API calls
    */
  def withWorkspace(namespace: String, namePrefix: String, authDomain: Set[String] = Set.empty,
                    aclEntries: List[AclEntry] = List(),
                    attributes: Option[Map[String, String]] = None,
                    cleanUp: Boolean = true)
                   (testCode: (String) => Any)(implicit token: AuthToken): Unit = {
    val workspaceName = appendUnderscore(namePrefix) + makeUuid
    api.workspaces.create(namespace, workspaceName, authDomain)
    api.workspaces.updateAcl(namespace, workspaceName, aclEntries)
    if (attributes.isDefined)
      api.workspaces.setAttributes(namespace, workspaceName, attributes.get)
    try {
      testCode(workspaceName)
    } finally {
      if (cleanUp) {
        try {
          api.workspaces.delete(namespace, workspaceName)
        } catch nonFatalAndLog(s"Error deleting workspace in withWorkspace clean-up: $namespace/$workspaceName")
      }
    }
  }
}
