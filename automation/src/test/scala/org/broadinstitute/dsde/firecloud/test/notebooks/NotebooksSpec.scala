package org.broadinstitute.dsde.firecloud.test.notebooks

import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.firecloud.page.workspaces.notebooks.WorkspaceNotebooksPage
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{Config, UserPool}
import org.broadinstitute.dsde.workbench.fixture.{MethodFixtures, WorkspaceFixtures}
import org.broadinstitute.dsde.workbench.service.test.{CleanUp, WebBrowserSpec}
import org.broadinstitute.dsde.workbench.service.util.Retry.retry
import org.openqa.selenium.WebDriver
import org.scalatest.{FreeSpec, Matchers}
import scala.concurrent.duration.DurationLong


import scala.util.Try

class NotebooksSpec extends FreeSpec with WebBrowserSpec with CleanUp with WorkspaceFixtures with UserFixtures with MethodFixtures with Matchers with LazyLogging {

  val billingProject: String = Config.Projects.default
  val numberOfWorkersError = "Error: Google Dataproc does not support clusters with 1 non-preemptible worker. Must be 0, 2 or more."
  val noNameClusterError = "Cluster name cannot be empty"


  "User should be able to create and delete a cluster" in withWebDriver { implicit driver =>
    val user = UserPool.chooseStudent
    implicit val authToken: AuthToken = user.makeAuthToken()
    withWorkspace(billingProject, "NotebooksSpec_create_delete_cluster") { workspaceName =>
      withSignIn(user) { _ =>
        val clusterName = "notebooksspec-" + UUID.randomUUID()
        val notebooksPage = new WorkspaceNotebooksPage(billingProject, workspaceName).open

        def createCluster() = {
          logger.info("Attempting to create dataproc cluster")
          val createModal = notebooksPage.openCreateClusterModal
          createModal.createCluster(clusterName)
          createModal.awaitDismissed
          assert(notebooksPage.getClusterStatus(clusterName) == "Creating")
          logger.info("Creating dataproc cluster " + clusterName)
          notebooksPage.waitUntilClusterIsDoneCreating(clusterName)
        }

        def deleteCluster() = {
          logger.info("Attempting to delete dataproc cluster")
          notebooksPage.openDeleteClusterModal(clusterName).deleteCluster
          await condition (notebooksPage.getClusterStatus(clusterName) == "Deleting")
          logger.info("Deleting dataproc cluster " + clusterName)
          notebooksPage.waitUntilClusterIsDeleted(clusterName)
          await text notebooksPage.noClustersMessage
          logger.info("Deleted dataproc cluster " + clusterName)
        }

        def checkJupyterPageAndDelete() = {
          val jupyterPageResult: Try[WebDriver] = Try {
            notebooksPage.openJupyterPage(clusterName).returnToNotebooksList()
          }
          deleteCluster()
          jupyterPageResult.get
        }

        createCluster()
        notebooksPage.getClusterStatus(clusterName) match {
          case "View Error" => {
            deleteCluster()
            createCluster()
            notebooksPage.getClusterStatus(clusterName) match {
              case "View Error" => {
                val errorMessage = notebooksPage.openClusterErrorModal(clusterName).getErrorText()
                logger.info(s"Cluster $clusterName errored with Message $errorMessage")
              }
              case "Running" => {
                checkJupyterPageAndDelete()
              }
            }
          }
          case "Running" => {
            checkJupyterPageAndDelete()
          }
        }

//        //dataproc names must be lower case
//        val clusterName = "notebooksspec-" + UUID.randomUUID()
//        val notebooksPage = new WorkspaceNotebooksPage(billingProject, workspaceName).open
//
//        retry[String](100.milliseconds, 1.minute)({
//          logger.info("Attempting to create dataproc cluster")
//          val createModal = notebooksPage.openCreateClusterModal
//          createModal.createCluster(clusterName)
//          createModal.awaitDismissed
//          assert(notebooksPage.getClusterStatus(clusterName) == "Creating")
//          logger.info("Creating dataproc cluster " + clusterName)
//          notebooksPage.waitUntilClusterIsDoneCreating(clusterName)
//          Some(notebooksPage.getClusterStatus(clusterName))
//        }) match {
//          case Some("Running") => {
//
//          }
//          case Some("View Error") => {
//            //open cluster error modal and get error text
//            logger.info(s"Cluster $clusterName errored with Message $errorMessage")
//          }
//          case Some(s) =>
//        }
//
//        // cluster creation
//        // add a retry in case cluster creation failed for some transient reason
//          notebooksPage.openDeleteClusterModal(clusterName).deleteCluster
//          logger.info("Deleting errored dataproc cluster " + clusterName)
//          notebooksPage.waitUntilClusterIsDeleted(clusterName)
//          logger.info("Deleted errored dataproc cluster " + clusterName)
//          await text notebooksPage.noClustersMessage
//
//          logger.info(s"Retrying dataproc cluster creation for $clusterName")
//          val createModal = notebooksPage.openCreateClusterModal
//          createModal.createCluster(clusterName)
//          createModal.awaitDismissed
//          assert(notebooksPage.getClusterStatus(clusterName) == "Creating")
//          logger.info("Recreating dataproc cluster " + clusterName)
//          notebooksPage.waitUntilClusterIsDoneCreating(clusterName)
//
//
//
//        logger.info("Created dataproc cluster " + clusterName)
//        val jupyterPageResult: Try[WebDriver] = Try {
//          notebooksPage.openJupyterPage(clusterName).returnToNotebooksList()
//        }
//
//        //Cluster deletion
//        logger.info("Attempting to delete dataproc cluster")
//        notebooksPage.openDeleteClusterModal(clusterName).deleteCluster
//        await condition (notebooksPage.getClusterStatus(clusterName) == "Deleting")
//        logger.info("Deleting dataproc cluster " + clusterName)
//        notebooksPage.waitUntilClusterIsDeleted(clusterName)
//        await text notebooksPage.noClustersMessage
//        logger.info("Deleted dataproc cluster " + clusterName)
//
//        jupyterPageResult.get
      }
    }
  }

  "Creating 1 worker cluster should return server error" in withWebDriver { implicit driver =>
    val user = UserPool.chooseStudent
    implicit val authToken: AuthToken = user.makeAuthToken()

    withWorkspace(billingProject, "NotebookSpec_1_worker_error") { workspaceName =>
      withSignIn(user) {_ =>
        val clusterName = "notebookspec-1worker-" + UUID.randomUUID()
        val notebooksPage = new WorkspaceNotebooksPage(billingProject, workspaceName).open

        logger.info("Attempting to create a 1 worker cluster")
        val createModal = notebooksPage.openCreateClusterModal()
        createModal.createCluster(clusterName, workers = 1)
        await text numberOfWorkersError
      }
    }
  }

  "Creating cluster without name should fail at input validation" in withWebDriver { implicit driver =>
    val user = UserPool.chooseStudent
    implicit val authToken: AuthToken = user.makeAuthToken()

    withWorkspace(billingProject, "NotebookSpec_no_cluster_name") { workspaceName =>
      withSignIn(user) {_ =>
        val clusterName = "notebookspec-no-cluster-name-" + UUID.randomUUID()
        val notebooksPage = new WorkspaceNotebooksPage(billingProject, workspaceName).open

        logger.info("Attempting to create a cluster with no name ")
        val createModal = notebooksPage.openCreateClusterModal()
        createModal.createCluster("")
        await text noNameClusterError
      }
    }
  }

}
