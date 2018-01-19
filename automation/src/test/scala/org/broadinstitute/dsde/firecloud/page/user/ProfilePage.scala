package org.broadinstitute.dsde.firecloud.page.user

import org.broadinstitute.dsde.firecloud.component.Button
import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.firecloud.config.Config
import org.broadinstitute.dsde.firecloud.page.{BaseFireCloudPage, PageUtil}
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page

class ProfilePage(implicit webDriver: WebDriver) extends BaseFireCloudPage
  with Page with PageUtil[ProfilePage] {

  override val url: String = s"${Config.FireCloud.baseUrl}#profile"

  private val proxyGroupEmailQuery = testId("proxyGroupEmail")
  private val saveProfileButton = Button("save-profile-button")

  override def awaitReady(): Unit = {
    saveProfileButton.awaitVisible()
  }

  def readProxyGroupEmail: String = {
    readText(proxyGroupEmailQuery)
  }
}
