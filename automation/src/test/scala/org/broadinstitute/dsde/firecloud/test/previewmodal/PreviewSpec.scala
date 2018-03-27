package org.broadinstitute.dsde.firecloud.test.previewmodal

import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{Config, UserPool}
import org.broadinstitute.dsde.workbench.fixture.{MethodFixtures, WorkspaceFixtures}
import org.broadinstitute.dsde.workbench.service.test.{CleanUp, WebBrowserSpec}
import org.broadinstitute.dsde.workbench.service.util.Retry.retry
import org.scalatest.{FreeSpec, Matchers}
import scala.concurrent.duration.DurationLong

class PreviewSpec extends FreeSpec with WebBrowserSpec with WorkspaceFixtures with UserFixtures with MethodFixtures
  with CleanUp with Matchers {

  val billingProject: String = Config.Projects.default

  val gsLink = "gs://commons-dss-commons/blobs/64573c6a0c75993c16e313f819fa71b8571b86de75b7523ae8677a92172ea2ba.9976538e92c4f12aebfea277ecaef9fc5b54c732.594f5f1a316e9ccfb38d02a345c86597-293.41a4b033"
  val dosLink = "dos://spbnq0bc10.execute-api.us-west-2.amazonaws.com/ed703a5d-4705-49a8-9429-5169d9225bbd"

  //These tests utilize the view via workspace, but the same code is used throughout
  //the UI to render this modal, so should work in all cases.

  "UI should correctly render gcs links" in withWebDriver { implicit driver =>
    val user = UserPool.chooseStudent
    implicit val authToken: AuthToken = user.makeAuthToken()
    withWorkspace(billingProject, "WorkspaceSpec_gs_link_ws_attrs") { workspaceName =>
      withSignIn(user) { listPage =>
        val detailPage = listPage.enterWorkspace(billingProject, workspaceName)
        detailPage.edit {
          detailPage.addWorkspaceAttribute("a", gsLink)
        }
        detailPage.readWorkspaceTableLinks shouldBe List(gsLink)
      }
    }
  }

  "UI should correctly render dos links" in withWebDriver { implicit driver =>
    val user = UserPool.chooseStudent
    implicit val authToken: AuthToken = user.makeAuthToken()
    withWorkspace(billingProject, "WorkspaceSpec_dos_link_ws_attrs") { workspaceName =>
      withSignIn(user) { listPage =>
        val detailPage = listPage.enterWorkspace(billingProject, workspaceName)
        detailPage.edit {
          detailPage.addWorkspaceAttribute("a", dosLink)
        }
        detailPage.readWorkspaceTableLinks shouldBe List(dosLink)
      }
    }
  }

  "UI should not link if its not a gcs or dos link" in withWebDriver { implicit driver =>
    val user = UserPool.chooseStudent
    implicit val authToken: AuthToken = user.makeAuthToken()
    withWorkspace(billingProject, "WorkspaceSpec_no_link_ws_attrs") { workspaceName =>
      withSignIn(user) { listPage =>
        val detailPage = listPage.enterWorkspace(billingProject, workspaceName)
        detailPage.edit {
          detailPage.addWorkspaceAttribute("a", "X")
        }
        detailPage.readWorkspaceTableLinks shouldBe List()
      }
    }
  }

  "Preview Modal should work correctly for gcs link" in withWebDriver { implicit driver =>
    val user = UserPool.chooseStudent
    implicit val authToken: AuthToken = user.makeAuthToken()
    withWorkspace(billingProject, "WorkspaceSpec_gcs_link_preview") { workspaceName =>
      withSignIn(user) { listPage =>
        val detailPage = listPage.enterWorkspace(billingProject, workspaceName)
        detailPage.edit {
          //this is a known file in our bucket
          detailPage.addWorkspaceAttribute("a", "gs://firecloud-alerts-dev/alerts.json")
        }
        val previewModal = detailPage.clickForPreview("gs://firecloud-alerts-dev/alerts.json")
        previewModal.awaitReady()
        previewModal.getBucket shouldBe "Google Bucket: firecloud-alerts-dev"
        previewModal.getObject shouldBe "Object: alerts.json"
        // preview pane is only created if there's something to preview so
        // give it .1 sec
        retry[Boolean](100.milliseconds, 1.minute)({
          val previewPane = previewModal.findInner("preview-pane")
          if (previewPane.webElement.isDisplayed)
            Some(true)
          else None
        }) match {
          case None => fail()
          case Some(s) => s shouldBe true
        }
        val previewPane = previewModal.findInner("preview-pane")
        //file sometimes changes but is always a JSON array, so easy test...
        previewPane.webElement.getText should startWith("[")
        previewPane.webElement.getText should endWith("]")
        previewModal.getPreviewMessage shouldBe "Previews may not be supported for some filetypes."
      }
    }
  }

  "Preview Modal should display correct message when file not previewable" in withWebDriver { implicit driver =>
    val user = UserPool.chooseStudent
    implicit val authToken: AuthToken = user.makeAuthToken()
    val bucket = "firecloud-tcga-protected-dummy-dev"
    val gObject = "HCC1143.100_gene_250bp_pad.bam"
    withWorkspace(billingProject, "WorkspaceSpec_gcs_file_not_previewable") { workspaceName =>
      withSignIn(user) { listPage =>
        val detailPage = listPage.enterWorkspace(billingProject, workspaceName)
        detailPage.edit {
          //this is a known file in our bucket
          detailPage.addWorkspaceAttribute("a", s"gs://$bucket/$gObject")
        }
        val previewModal = detailPage.clickForPreview(s"gs://$bucket/$gObject")
        previewModal.awaitReady()
        previewModal.getBucket shouldBe s"Google Bucket: $bucket"
        previewModal.getObject shouldBe s"Object: $gObject"
        previewModal.getPreviewMessage shouldBe "Preview is not supported for this filetype."
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
    withWorkspace(billingProject, "WorkspaceSpec_dos_file_not_previewable") { workspaceName =>
      withSignIn(user) { listPage =>
        val detailPage = listPage.enterWorkspace(billingProject, workspaceName)
        detailPage.edit {
          //this is a known file in our bucket
          detailPage.addWorkspaceAttribute("a", dosLink)
        }
        val previewModal = detailPage.clickForPreview(dosLink)
        previewModal.awaitDoneState()
        previewModal.getBucket shouldBe s"Google Bucket: $bucket"
        previewModal.getObject shouldBe s"Object: $gObject"
        previewModal.getPreviewMessage shouldBe "Previews may not be supported for some filetypes."
        previewModal.getErrorMessage shouldBe "Error! You do not have access to this file.\nShow full error response"
      }
    }
  }
}