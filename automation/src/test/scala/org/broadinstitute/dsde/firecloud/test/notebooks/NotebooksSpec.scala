package org.broadinstitute.dsde.firecloud.test.notebooks

import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.firecloud.page.workspaces.notebooks.WorkspaceNotebooksPage
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{Credentials, UserPool}
import org.broadinstitute.dsde.workbench.fixture.{BillingFixtures, MethodFixtures, TestReporterFixture, WorkspaceFixtures}
import org.broadinstitute.dsde.workbench.service.test.{CleanUp, WebBrowserSpec}
import org.broadinstitute.dsde.workbench.service.Orchestration.billing.BillingProjectRole
import org.openqa.selenium.WebDriver
import org.scalatest.{FreeSpec, Matchers}

import scala.util.Try

class NotebooksSpec extends FreeSpec with WebBrowserSpec with CleanUp with WorkspaceFixtures with UserFixtures
  with BillingFixtures with MethodFixtures with Matchers with LazyLogging  with TestReporterFixture {

  val numberOfWorkersError = "Error: Google Dataproc does not support clusters with 1 non-preemptible worker. Must be 0, 2 or more."
  val noNameClusterError = "Cluster name cannot be empty"

  // TODO: these tests are currently ignored because they fail intermittently. The failure is due to Leo receiving 401 errors
  // from Sam. These tests should be un-ignored once the issue has been addressed.
  // See https://github.com/DataBiosphere/leonardo/issues/290 for more information.

  "User should be able to create and delete a cluster" ignore {
    val ownerUser: Credentials = UserPool.chooseProjectOwner
    val ownerToken = ownerUser.makeAuthToken()

    val user = UserPool.chooseStudent
    val userToken: AuthToken = user.makeAuthToken()

    withCleanBillingProject(ownerUser) { projectName =>
      api.billing.addUserToBillingProject(projectName, user.email, BillingProjectRole.User)(ownerToken)

      withWorkspace(projectName, "NotebooksSpec_create_delete_cluster") { workspaceName =>

        withWebDriver { implicit driver =>
          withSignIn(user) { _ =>
            //dataproc names must be lower case
            val clusterName = "notebooksspec-" + UUID.randomUUID()
            val notebooksPage = new WorkspaceNotebooksPage(projectName, workspaceName).open

            // Cluster creation
            logger.info("Attempting to create dataproc cluster")
            val createModal = notebooksPage.openCreateClusterModal
            createModal.createCluster(clusterName)
            createModal.awaitDismissed()
            assert(notebooksPage.getClusterStatus(clusterName) == "Creating")
            logger.info("Creating dataproc cluster " + clusterName)
            notebooksPage.waitUntilClusterIsRunning(clusterName)
            logger.info("Created dataproc cluster " + clusterName)

            val jupyterPageResult: Try[WebDriver] = Try {
              notebooksPage.openJupyterPage(clusterName).returnToNotebooksList()
            }

            //Cluster deletion
            logger.info("Attempting to delete dataproc cluster")
            notebooksPage.openDeleteClusterModal(clusterName).deleteCluster
            await condition (notebooksPage.getClusterStatus(clusterName) == "Deleting")
            logger.info("Deleting dataproc cluster " + clusterName)
            notebooksPage.waitUntilClusterIsDeleted(clusterName)
            await text notebooksPage.noClustersMessage
            logger.info("Deleted dataproc cluster " + clusterName)

            jupyterPageResult.get
          }
        }
      }(userToken)
    }
  }

  "Creating 1 worker cluster should return server error" ignore {
    val ownerUser: Credentials = UserPool.chooseProjectOwner
    val ownerToken = ownerUser.makeAuthToken()

    val user = UserPool.chooseStudent
    val userToken: AuthToken = user.makeAuthToken()

    withCleanBillingProject(ownerUser) { projectName =>
      api.billing.addUserToBillingProject(projectName, user.email, BillingProjectRole.User)(ownerToken)

      withWorkspace(projectName, "NotebookSpec_1_worker_error") { workspaceName =>
        withWebDriver { implicit driver =>
          withSignIn(user) { _ =>
            val clusterName = "notebookspec-1worker-" + UUID.randomUUID()
            val notebooksPage = new WorkspaceNotebooksPage(projectName, workspaceName).open

            logger.info("Attempting to create a 1 worker cluster")
            val createModal = notebooksPage.openCreateClusterModal()
            createModal.createCluster(clusterName, workers = 1)
            await text numberOfWorkersError
          }
        }
      }(userToken)
    }
  }

  "Creating cluster without name should fail at input validation" ignore {
    val ownerUser: Credentials = UserPool.chooseProjectOwner
    val ownerToken = ownerUser.makeAuthToken()

    val user = UserPool.chooseStudent
    val userToken: AuthToken = user.makeAuthToken()

    withCleanBillingProject(ownerUser) { projectName =>
      api.billing.addUserToBillingProject(projectName, user.email, BillingProjectRole.User)(ownerToken)

      withWorkspace(projectName, "NotebookSpec_no_cluster_name") { workspaceName =>
        withWebDriver { implicit driver =>
          withSignIn(user) { _ =>
            val clusterName = "notebookspec-no-cluster-name-" + UUID.randomUUID()
            val notebooksPage = new WorkspaceNotebooksPage(projectName, workspaceName).open

            logger.info("Attempting to create a cluster with no name ")
            val createModal = notebooksPage.openCreateClusterModal()
            createModal.createCluster("")
            await text noNameClusterError
          }
        }
      }(userToken)
    }
  }

}
