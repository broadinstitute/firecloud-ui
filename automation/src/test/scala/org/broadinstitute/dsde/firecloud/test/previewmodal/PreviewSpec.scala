package org.broadinstitute.dsde.firecloud.test.previewmodal
// checkme

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
  val dosLink = "dos://broad-dsp-dos.storage.googleapis.com/dos.json"

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
          previewModal.getPreviewMessage shouldBe "Previews may not be supported for some filetypes."

          previewModal.xOut()
        }
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
          previewModal.getPreviewMessage shouldBe "Preview is not supported for this filetype."

          previewModal.xOut()
        }
      }
    }
  }

  "Preview Modal should display correct message and preview when file is accessible for dos:// link" in withWebDriver { implicit driver =>
    val user = UserPool.chooseStudent
    implicit val authToken: AuthToken = user.makeAuthToken()
    val dosLink = "dos://broad-dsp-dos.storage.googleapis.com/preview_dos.json"
    val bucket = "broad-dsp-dos"
    val gObject = "dos_test.txt"
    withCleanBillingProject(user) { billingProject =>
      withWorkspace(billingProject, "WorkspaceSpec_dos_file_previewable") { workspaceName =>
        api.workspaces.setAttributes(billingProject, workspaceName, Map("a" -> dosLink))
        withSignIn(user) { listPage =>
          val detailPage = listPage.enterWorkspace(billingProject, workspaceName)
          val previewModal = detailPage.clickForPreview(dosLink)
          previewModal.getBucket shouldBe s"$bucket"
          previewModal.getObject shouldBe s"$gObject"
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
          previewPane.webElement.getText shouldBe "This file is for test purposes."
          previewModal.getPreviewMessage shouldBe "Previews may not be supported for some filetypes."

          previewModal.xOut()
        }
      }
    }
  }

  "Preview Modal should display correct message when file not previewable for dos:// link" in withWebDriver { implicit driver =>
    val user = UserPool.chooseStudent
    implicit val authToken: AuthToken = user.makeAuthToken()
    val bucket = "broad-public-datasets"
    val gObject = "NA12878_downsampled_for_testing/unmapped/H06JUADXX130110.1.ATCACGAT.20k_reads.bam"
    withCleanBillingProject(user) { billingProject =>
      withWorkspace(billingProject, "WorkspaceSpec_dos_file_not_previewable") { workspaceName =>
        //this is a known file in our bucket
        api.workspaces.setAttributes(billingProject, workspaceName, Map("a" -> dosLink))
        withSignIn(user) { listPage =>
          val detailPage = listPage.enterWorkspace(billingProject, workspaceName)
          val previewModal = detailPage.clickForPreview(dosLink)
          previewModal.getBucket shouldBe s"$bucket"
          previewModal.getObject shouldBe s"$gObject"
          previewModal.getPreviewMessage shouldBe "Preview is not supported for this filetype."

          previewModal.xOut()
        }
      }
    }
  }
}
