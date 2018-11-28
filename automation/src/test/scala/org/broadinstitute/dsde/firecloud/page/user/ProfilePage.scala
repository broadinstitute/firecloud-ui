package org.broadinstitute.dsde.firecloud.page.user

import org.broadinstitute.dsde.firecloud.FireCloudConfig
import org.broadinstitute.dsde.firecloud.component.{Button, Link}
import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.firecloud.page.{BaseFireCloudPage, PageUtil}
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page

class ProfilePage(implicit webDriver: WebDriver) extends BaseFireCloudPage
  with Page with PageUtil[ProfilePage] {

  override val url: String = s"${FireCloudConfig.FireCloud.baseUrl}#profile"

  private val proxyGroupEmailQuery = testId("proxyGroupEmail")
  private val saveProfileButton = Button("save-profile-button")
  private val fenceLink = Link("fence")
  private val dcfFenceLink = Link("dcf-fence")

  override def awaitReady(): Unit = {
    saveProfileButton.awaitVisible()
  }

  def readProxyGroupEmail: String = {
    // wait for the div containing the proxy group email to be visible
    await visible (proxyGroupEmailQuery, 10)
    // wait for the proxy group email to be populated inside the div; this can happen asynchronously after the div
    // itself is rendered
    await condition {
      readText(proxyGroupEmailQuery).nonEmpty
    }
    readText(proxyGroupEmailQuery)
  }

  def linkFence: Unit = {
    fenceLink.awaitVisible()
    fenceLink.doClick()
  }

  def linkDcfFence: Unit = {
    dcfFenceLink.awaitVisible()
    dcfFenceLink.doClick()
  }
}
