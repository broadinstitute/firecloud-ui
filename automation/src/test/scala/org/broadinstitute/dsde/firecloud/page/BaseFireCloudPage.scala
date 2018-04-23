package org.broadinstitute.dsde.firecloud.page

import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.firecloud.component._
import org.broadinstitute.dsde.firecloud.page.library.DataLibraryPage
import org.broadinstitute.dsde.firecloud.page.methodrepo.MethodRepoPage
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceListPage
import org.openqa.selenium.WebDriver

abstract class BaseFireCloudPage(implicit webDriver: WebDriver) extends AuthenticatedPage {

  private val workspacesNavLink = Link("workspace-nav-link")
  private val libraryNavLink = Link("library-nav-link")
  private val methodRepoNavLink = Link("method-repo-nav-link")

  private val trialBannerTitle = Label("trial-banner-title")

  private val trialBannerButton = Button("trial-banner-button")
  private val reviewButton = Button("review-terms-of-service")
  private val agreeTermsCheckbox = Checkbox("agree-terms")
  private val agreeCloudTermsCheckbox = Checkbox("agree-cloud-terms")
  private val acceptButton = Button("accept-terms-of-service")

  def isTrialBannerVisible: Boolean = trialBannerTitle.isVisible
  def getTrialBannerTitle: String = trialBannerTitle.getText

  def enrollInFreeTrial(): Unit = {
    trialBannerButton.doClick()
    reviewButton.doClick()
    agreeTermsCheckbox.ensureChecked()
    agreeCloudTermsCheckbox.ensureChecked()
    acceptButton.doClick()

    // Button can take a sec to enter the "loading" state
    trialBannerButton.awaitState("loading")
    trialBannerButton.awaitState("ready")
  }

  def goToWorkspaces(): WorkspaceListPage = {
    workspacesNavLink.doClick()
    await ready new WorkspaceListPage
  }

  def goToDataLibrary(): DataLibraryPage = {
    libraryNavLink.doClick()
    await ready new DataLibraryPage
  }

  def goToMethodRepository(): MethodRepoPage = {
    methodRepoNavLink.doClick()
    await ready new MethodRepoPage()
  }
}
