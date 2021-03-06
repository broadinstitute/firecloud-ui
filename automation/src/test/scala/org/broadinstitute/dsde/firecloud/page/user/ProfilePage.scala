package org.broadinstitute.dsde.firecloud.page.user

import org.broadinstitute.dsde.firecloud.FireCloudConfig
import org.broadinstitute.dsde.firecloud.component.{Button, Link, TestId}
import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.firecloud.page.{BaseFireCloudPage, PageUtil}
import org.openqa.selenium.WebDriver

class ProfilePage(implicit webDriver: WebDriver) extends BaseFireCloudPage with PageUtil[ProfilePage] {

  lazy override val url: String = s"${FireCloudConfig.FireCloud.baseUrl}#profile"

  private val proxyGroupEmailQuery = testId("proxyGroupEmail")
  private val saveProfileButton = Button("save-profile-button")

  override def awaitReady(): Unit = {
    super.awaitReady()
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

  def awaitProvidersReady: Unit = {
    await notVisible (cssSelector("[data-test-id=spinner]"), 60)
  }

  def clickProviderLink(provider: String): Unit = {
    val providerLink = Link(TestId(provider))
    providerLink.awaitVisible()
    providerLink.doClick()
  }
}
