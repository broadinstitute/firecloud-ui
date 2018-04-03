package org.broadinstitute.dsde.firecloud.test.previewmodal

import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{Config, UserPool}
import org.broadinstitute.dsde.workbench.fixture.{BillingFixtures, MethodFixtures, WorkspaceFixtures}
import org.broadinstitute.dsde.workbench.service.test.{CleanUp, WebBrowserSpec}
import org.broadinstitute.dsde.workbench.service.util.Retry.retry
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.duration.DurationLong

class PreviewSpec extends FreeSpec with WebBrowserSpec with WorkspaceFixtures with UserFixtures with MethodFixtures with BillingFixtures
  with CleanUp with Matchers {

  val gsLink = "gs://broad-public-datasets/NA12878_downsampled_for_testing/unmapped/H06JUADXX130110.1.ATCACGAT.20k_reads.bam"
  val dosLink = "dos://dos-dss.ucsc-cgp-dev.org/60936d97-6358-4ce3-8136-d5776186ee21?version=2018-03-23T123738.145535Z"

  //These tests utilize the view via workspace, but the same code is used throughout
  //the UI to render this modal, so should work in all cases.

  "UI should correctly render gcs links" in withWebDriver { implicit driver =>
    val user = UserPool.chooseStudent
    implicit val authToken: AuthToken = user.makeAuthToken()
    withCleanBillingProject(user) { billingProject =>
      withWorkspace(billingProject, "WorkspaceSpec_gs_link_ws_attrs") { workspaceName =>
        api.workspaces.setAttributes(billingProject, workspaceName, Map("a" -> gsLink))
        withSignIn(user) { listPage =>
          val detailPage = listPage.enterWorkspace(billingProject, workspaceName)
          detailPage.readWorkspaceTableLinks shouldBe List(gsLink)
        }
      }
    }
  }

  "UI should correctly render dos links" in withWebDriver { implicit driver =>
    val user = UserPool.chooseStudent
    implicit val authToken: AuthToken = user.makeAuthToken()
    withCleanBillingProject(user) { billingProject =>
      withWorkspace(billingProject, "WorkspaceSpec_dos_link_ws_attrs") { workspaceName =>
        api.workspaces.setAttributes(billingProject, workspaceName, Map("a" -> dosLink))
        withSignIn(user) { listPage =>
          val detailPage = listPage.enterWorkspace(billingProject, workspaceName)
          detailPage.readWorkspaceTableLinks shouldBe List(dosLink)
        }
      }
    }
  }

  "UI should not link if its not a gcs or dos link" in withWebDriver { implicit driver =>
    val user = UserPool.chooseStudent
    implicit val authToken: AuthToken = user.makeAuthToken()
    withCleanBillingProject(user) { billingProject =>
      withWorkspace(billingProject, "WorkspaceSpec_no_link_ws_attrs") { workspaceName =>
        api.workspaces.setAttributes(billingProject, workspaceName, Map("a" -> "X"))
        withSignIn(user) { listPage =>
          val detailPage = listPage.enterWorkspace(billingProject, workspaceName)
          detailPage.readWorkspaceTableLinks shouldBe List()
        }
      }
    }
  }

  "Preview Modal should work correctly for gcs link" in withWebDriver { implicit driver =>
    val user = UserPool.chooseStudent
    implicit val authToken: AuthToken = user.makeAuthToken()
    withCleanBillingProject(user) { billingProject =>
      withWorkspace(billingProject, "WorkspaceSpec_gcs_link_preview") { workspaceName =>
        //this is a known file in our bucket
        api.workspaces.setAttributes(billingProject, workspaceName, Map("a" -> "gs://firecloud-alerts-dev/alerts.json"))
        withSignIn(user) { listPage =>
          val detailPage = listPage.enterWorkspace(billingProject, workspaceName)
          val previewModal = detailPage.clickForPreview("gs://firecloud-alerts-dev/alerts.json")

          previewModal.getBucket shouldBe "firecloud-alerts-dev"
          previewModal.getObject shouldBe "alerts.json"

          val filePreview = previewModal.getFilePreview
          //file sometimes changes but is always a JSON array, so easy test...
          filePreview should startWith("[")
          filePreview should endWith("]")
          previewModal.getPreviewMessage shouldBe "Previews may not be supported for some filetypes."}
      }
    }
  }

  "Preview Modal should display correct message when file not previewable" in withWebDriver { implicit driver =>
    val user = UserPool.chooseStudent
    implicit val authToken: AuthToken = user.makeAuthToken()
    val bucket = "firecloud-tcga-protected-dummy-dev"
    val gObject = "HCC1143.100_gene_250bp_pad.bam"
    withCleanBillingProject(user) { billingProject =>
      withWorkspace(billingProject, "WorkspaceSpec_gcs_file_not_previewable") { workspaceName =>
        //this is a known file in our bucket
        api.workspaces.setAttributes(billingProject, workspaceName, Map("a" -> s"gs://$bucket/$gObject"))
        withSignIn(user) { listPage =>
          val detailPage = listPage.enterWorkspace(billingProject, workspaceName)
          val previewModal = detailPage.clickForPreview(s"gs://$bucket/$gObject")

          previewModal.getBucket shouldBe bucket
          previewModal.getObject shouldBe gObject
          previewModal.getPreviewMessage shouldBe "Preview is not supported for this filetype."}
      }
    }
  }

  //TODO: also need a test to show preview does display when file is viewable, and previewable
  //But currently do not have a dos url that resolves to a bucket where that is the case.

  "Preview Modal should display correct message when file not accessible for dos:// link" in withWebDriver { implicit driver =>
    val user = UserPool.chooseStudent
    implicit val authToken: AuthToken = user.makeAuthToken()
    //TODO once we set up a version of this we control, switch this out (Martha tests too)
    val dosLink = "dos://spbnq0bc10.execute-api.us-west-2.amazonaws.com/ed703a5d-4705-49a8-9429-5169d9225bbd"
    val bucket = "commons-dss-commons"
    val gObject = "blobs/64573c6a0c75993c16e313f819fa71b8571b86de75b7523ae8677a92172ea2ba.9976538e92c4f12aebfea277ecaef9fc5b54c732.594f5f1a316e9ccfb38d02a345c86597-293.41a4b033"
    withCleanBillingProject(user) { billingProject =>
      withWorkspace(billingProject, "WorkspaceSpec_dos_file_not_previewable") { workspaceName =>
        //this is a known file in our bucket
        api.workspaces.setAttributes(billingProject, workspaceName, Map("a" -> dosLink))
        withSignIn(user) { listPage =>
          val detailPage = listPage.enterWorkspace(billingProject, workspaceName)
          val previewModal = detailPage.clickForPreview(dosLink)

          previewModal.getBucket shouldBe bucket
          previewModal.getObject shouldBe gObject
          previewModal.getPreviewMessage shouldBe "Previews may not be supported for some filetypes."
          previewModal.getErrorMessage shouldBe "Error! You do not have access to this file.\nShow full error response"
        }
      }
    }
  }
}
