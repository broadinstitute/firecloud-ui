package org.broadinstitute.dsde.firecloud.workspaces

import org.broadinstitute.dsde.firecloud.Util
import org.broadinstitute.dsde.firecloud.api.{AuthToken, service}

/**
  * Fixtures for creating and cleaning up test workspaces.
  */
trait WorkspaceFixtures {

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
  def withWorkspace(namespace: String, namePrefix: Option[String] = None, authDomain: Option[String] = None)(testCode: (String) => Any)(implicit token: AuthToken): Unit = {
    val workspaceName = namePrefix.getOrElse("") + "_" + Util.makeUuid
    service.workspaces.create(namespace, workspaceName, authDomain)
    try {
      testCode(workspaceName)
    } finally {
      service.workspaces.delete(namespace, workspaceName)
    }
  }
}