package org.broadinstitute.dsde.firecloud.test.notebooks

import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.firecloud.page.workspaces.notebooks.WorkspaceNotebooksPage
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{Config, UserPool}
import org.broadinstitute.dsde.workbench.fixture.{MethodFixtures, WorkspaceFixtures}
import org.broadinstitute.dsde.workbench.service.test.{CleanUp, WebBrowserSpec}
import org.scalatest.{FreeSpec, Matchers}

class NotebooksSpec extends FreeSpec with WebBrowserSpec with CleanUp with WorkspaceFixtures with UserFixtures with MethodFixtures with Matchers with LazyLogging {

  val billingProject: String = Config.Projects.default

  "User should be able to create and delete a cluster" in withWebDriver { implicit driver =>
    val user = UserPool.chooseStudent
    implicit val authToken: AuthToken = user.makeAuthToken()
    withWorkspace(billingProject, "NotebooksSpec_create_delete_cluster") { workspaceName =>
      withSignIn(user) { _ =>
        //name must be lower case
        val clusterName = "notebooksspec-" + UUID.randomUUID()
        new WorkspaceNotebooksPage(billingProject, workspaceName).open
          .withNewCluster(clusterName) { notebooksPage =>
            notebooksPage.openJupyterPage(clusterName).returnToNotebooksList()
          }
      }
    }
  }

  // maybe some other tests for ui - create cluster with 1 worker, no name, pre-emptible workers with 0 normal workers

}
