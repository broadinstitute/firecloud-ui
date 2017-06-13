package org.broadinstitute.dsde.firecloud.workspaces

import org.broadinstitute.dsde.firecloud.Util
import org.broadinstitute.dsde.firecloud.api.{AclEntry, AuthToken, service}
import org.broadinstitute.dsde.firecloud.pages.WebBrowserSpec
import org.broadinstitute.dsde.firecloud.auth.AuthToken

/**
  * Fixtures for creating and cleaning up test workspaces.
  */
trait WorkspaceFixtures[A <: WebBrowserSpec] { self: A =>

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
  def withWorkspace(namespace: String, namePrefix: Option[String] = None,
                    authDomain: Option[String] = None, aclEntries: List[AclEntry] = List())
                   (testCode: (String) => Any)(implicit token: AuthToken): Unit = {
    val workspaceName = namePrefix.getOrElse("") + "_" + Util.makeUuid
    api.workspaces.create(namespace, workspaceName, authDomain)
    api.workspaces.updateAcl(namespace, workspaceName, aclEntries)
    try {
      testCode(workspaceName)
    } finally {
      api.workspaces.delete(namespace, workspaceName)
    }
  }

  def withClonedWorkspace(namespace: String, namePrefix: Option[String] = None,
                          authDomain: Option[String] = None)
                         (testCode: (String) => Any)(implicit token: AuthToken): Unit = {
    withWorkspace(namespace, namePrefix, authDomain) { _ =>
      val cloneNamePrefix = Option(namePrefix.map(_ + "_clone").getOrElse("clone"))
      withWorkspace(namespace, cloneNamePrefix, authDomain)(testCode)
    }
  }
}
