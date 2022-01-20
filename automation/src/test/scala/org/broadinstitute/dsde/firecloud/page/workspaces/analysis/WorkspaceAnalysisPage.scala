package org.broadinstitute.dsde.firecloud.page.workspaces.analysis

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.FireCloudConfig
import org.broadinstitute.dsde.firecloud.page.PageUtil
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspacePage
import org.openqa.selenium.WebDriver


class WorkspaceAnalysisPage(namespace: String, name: String)(implicit webDriver: WebDriver)
  extends WorkspacePage(namespace, name) with PageUtil[WorkspaceAnalysisPage] with LazyLogging {

  override def awaitReady(): Unit = {
    await condition igvContainerSelector.findElement.exists(_.isDisplayed)
  }

  lazy override val url: String = s"${FireCloudConfig.FireCloud.baseUrl}#workspaces/$namespace/$name/analysis"

  // igv container is rendered by FireCloud's CLJS code
  private val igvContainerSelector = cssSelector(s"[data-test-id='igv-container']")

  // igv navbar is rendered by the third-party IGV JavaScript code. If this navbar is rendered/visible,
  // we know that IGV has not failed catastrophically.
  private val igvNavbarSelector = cssSelector("div.igv-navbar") inside igvContainerSelector

  def igvNavbarVisible: Boolean = {
    igvNavbarSelector.findElement.exists(_.isDisplayed)
  }

}
