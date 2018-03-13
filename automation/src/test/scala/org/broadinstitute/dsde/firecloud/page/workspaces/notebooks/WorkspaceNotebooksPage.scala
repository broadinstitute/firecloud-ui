package org.broadinstitute.dsde.firecloud.page.workspaces.notebooks

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.FireCloudView
import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.firecloud.component._
import org.broadinstitute.dsde.firecloud.page.PageUtil
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspacePage
import org.broadinstitute.dsde.workbench.config.Config
import org.broadinstitute.dsde.workbench.model.google.GcsPath
import org.broadinstitute.dsde.workbench.service.test.WebBrowserUtil
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.{Page, WebBrowser}

class WorkspaceNotebooksPage(namespace: String, name: String)(implicit webDriver: WebDriver)
  extends WorkspacePage(namespace, name) with Page with PageUtil[WorkspaceNotebooksPage] with LazyLogging {

  override def awaitReady(): Unit = clustersTable.awaitReady()

  override val url: String = s"${Config.FireCloud.baseUrl}#workspaces/$namespace/$name/notebooks"

  private val clustersTable = Table("spark-clusters-table")
  private val openCreateClusterModalButton: Button = Button("create-modal-button")
  val noClustersMessage = "There are no clusters to display."
  def clusterStatusId(clusterName: String) = clusterName + "-status"

  // Goes to a Jupyter page. Currently doesn't return a page object
  // because Jupyter functionality is tested in leo automated tests.
  // Just check that it loads
  def openJupyterPage(clusterName: String): JupyterPage = {
    val initialWindowHandles = windowHandles

    val clusterLink = Link(clusterName + "-link")
    clusterLink.doClick()

    await condition (windowHandles.size > 1)
    val jupyterWindowHandle = (windowHandles -- initialWindowHandles).head

    switch to window(jupyterWindowHandle)
    new JupyterPage().awaitLoaded()
  }

  def waitUntilClusterIsRunning(clusterName: String) = {
    await condition (getClusterStatus(clusterName) == "Running", 900)
  }

  def waitUntilClusterIsDeleted(clusterName: String) = {
    await notVisible (testId(clusterStatusId(clusterName)), 900)
  }

  def openCreateClusterModal(): CreateClusterModal = {
    openCreateClusterModalButton.doClick()
    await ready new CreateClusterModal
  }

  def openDeleteClusterModal(clusterName: String): DeleteClusterModal = {
    val openDeleteClusterModalButton: Button = Button(clusterName + "-delete-button")

    openDeleteClusterModalButton.doClick()
    await ready new DeleteClusterModal(clusterName)
  }

  def getClusterStatus(clusterName: String): String = {
    val clusterStatus = Label(clusterStatusId(clusterName))
    await ready clusterStatus
    clusterStatus.getText
  }

  def getClusters() = {
    clustersTable.getData
  }
}


class CreateClusterModal(implicit webDriver: WebDriver) extends OKCancelModal("create-cluster-modal") {

  // ids for all inputs and labels (test ids for labels should increment
  private val clusterNameField = TextField("cluster-name-input" inside this)


  private val optionalSettingsArea = Collapse("optional-settings", new FireCloudView {
    override def awaitReady(): Unit = masterMachineTypeSelect.awaitVisible()

    val masterMachineTypeSelect = Select("master-machine-type-select")
    val masterDiskSizeField = NumberField("master-disk-size-input")
    val workerField = NumberField("workers-input")
    val workerMachineTypeSelect = Select("worker-machine-type-select")
    val workerDiskSizeField = NumberField("worker-disk-size-input")
    val workerLocalSSDsField = NumberField("worker-local-ssds-input")
    val localPreemptibleWorkersField = NumberField("preemptible-workers-input")
    val extensionURIField = TextField("extension-uri-input")
    val customScriptURIField = TextField("custom-script-uri-input")
    val addLabelButton = Button("add-label-button")

  })


  import scala.language.reflectiveCalls

  def createCluster(clusterName: String,
                    masterMachineType: String = "n1-standard-4",
                    masterDiskSize: Int = 500,
                    workers: Int = 0,
                    workerMachineType: String = "n1-standard-4",
                    workerDiskSize: Int = 500,
                    workerLocalSSDs: Int = 0,
                    preemptibleWorkers: Int = 0,
                    extensionURI: Option[GcsPath] = None,
                    customScriptURI: Option[GcsPath] = None,
                    labels: Map[String, String] = Map()) = {

    clusterNameField.setText(clusterName)
    optionalSettingsArea.toggle

    optionalSettingsArea.getInner.masterMachineTypeSelect.select(masterMachineType)
    optionalSettingsArea.getInner.masterDiskSizeField.setText(masterDiskSize.toString)

    optionalSettingsArea.getInner.workerField.setText(workers.toString)
    optionalSettingsArea.getInner.workerMachineTypeSelect.select(workerMachineType)
    optionalSettingsArea.getInner.workerDiskSizeField.setText(workerDiskSize.toString)

    optionalSettingsArea.getInner.workerLocalSSDsField.setText(workerLocalSSDs.toString)
    optionalSettingsArea.getInner.localPreemptibleWorkersField.setText(preemptibleWorkers.toString)

    extensionURI    foreach {path => optionalSettingsArea.getInner.extensionURIField.setText(path.toUri) }
    customScriptURI foreach {path => optionalSettingsArea.getInner.customScriptURIField.setText(path.toUri) }

    addLabels(labels)
    clickOk()
  }

  def addLabels(labels: Map[String, String]) = {
    optionalSettingsArea.ensureExpanded()
    var labelIndex = 0
    labels.map{ label =>
      optionalSettingsArea.getInner.addLabelButton.doClick()
      val keyField = TextField("key-" + labelIndex + "-input")
      val valueField = TextField("value-" + labelIndex + "-input")

      keyField.awaitVisible()
      valueField.awaitVisible()

      keyField.setText(label._1)
      valueField.setText(label._2)

      labelIndex+=1
    }
  }


}

class DeleteClusterModal(clusterName: String)(implicit webDriver: WebDriver) extends OKCancelModal("delete-cluster-modal") {
  def deleteCluster() = {
    submit
  }
}

class JupyterPage(implicit webDriver: WebDriver) extends WebBrowser with WebBrowserUtil {

  def awaitLoaded(): JupyterPage = {
    await text "Select items to perform actions on them."
    this
  }

  /**
    * Assumes we only have the notebooks list page and the jupyter page open
    */
  def returnToNotebooksList() = {
    switch to window((windowHandles - windowHandle).head)
  }
}
