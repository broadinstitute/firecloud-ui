package org.broadinstitute.dsde.firecloud.test.previewmodal

import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.UserPool
import org.broadinstitute.dsde.workbench.fixture.{BillingFixtures, MethodFixtures, WorkspaceFixtures}
import org.broadinstitute.dsde.workbench.service.test.WebBrowserSpec
import org.broadinstitute.dsde.workbench.service.util.Retry.retry
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FreeSpec, Matchers, ParallelTestExecution}

import scala.concurrent.duration.DurationLong

class PreviewSpec extends FreeSpec with ParallelTestExecution with WebBrowserSpec with WorkspaceFixtures with UserFixtures
  with MethodFixtures with BillingFixtures with Matchers {

  implicit override val patienceConfig =
    PatienceConfig(timeout = scaled(Span(10, Seconds)), interval = scaled(Span(500, Millis)))

  val gsLink = "gs://broad-public-datasets/NA12878_downsampled_for_testing/unmapped/H06JUADXX130110.1.ATCACGAT.20k_reads.bam"
  val dosLink = "dos://broad-dsp-dos.storage.googleapis.com/dos.json"

  //These tests utilize the view via workspace, but the same code is used throughout
  //the UI to render this modal, so should work in all cases.

  "UI should correctly render gcs links" in {
    val user = UserPool.chooseStudent
    implicit val authToken: AuthToken = user.makeAuthToken()
    withCleanBillingProject(user) { billingProject =>
      withWorkspace(billingProject, "WorkspaceSpec_gs_link_ws_attrs") { workspaceName =>
        api.workspaces.setAttributes(billingProject, workspaceName, Map("a" -> gsLink))
        withWebDriver { implicit driver =>
          withSignIn(user) { listPage =>
            val detailPage = listPage.enterWorkspace(billingProject, workspaceName)
            eventually { detailPage.readWorkspaceTableLinks shouldBe List(gsLink) }
          }
        }
      }
    }
  }

  "UI should correctly render dos links" in {
    val user = UserPool.chooseStudent
    implicit val authToken: AuthToken = user.makeAuthToken()
    withCleanBillingProject(user) { billingProject =>
      withWorkspace(billingProject, "WorkspaceSpec_dos_link_ws_attrs") { workspaceName =>
        api.workspaces.setAttributes(billingProject, workspaceName, Map("a" -> dosLink))
        withWebDriver { implicit driver =>
          withSignIn(user) { listPage =>
            val detailPage = listPage.enterWorkspace(billingProject, workspaceName)
            eventually { detailPage.readWorkspaceTableLinks shouldBe List(dosLink) }
          }
        }
      }
    }
  }

  "UI should not link if its not a gcs or dos link" in {
    val user = UserPool.chooseStudent
    implicit val authToken: AuthToken = user.makeAuthToken()
    withCleanBillingProject(user) { billingProject =>
      withWorkspace(billingProject, "WorkspaceSpec_no_link_ws_attrs") { workspaceName =>
        api.workspaces.setAttributes(billingProject, workspaceName, Map("a" -> "X"))
        withWebDriver { implicit driver =>
          withSignIn(user) { listPage =>
            val detailPage = listPage.enterWorkspace(billingProject, workspaceName)
            eventually { detailPage.readWorkspaceTableLinks shouldBe List() }
          }
        }
      }
    }
  }

  "Preview Modal should work correctly for gcs link" in {
    val user = UserPool.chooseStudent
    implicit val authToken: AuthToken = user.makeAuthToken()
    withCleanBillingProject(user) { billingProject =>
      withWorkspace(billingProject, "WorkspaceSpec_gcs_link_preview") { workspaceName =>
        //this is a known file in our bucket
        api.workspaces.setAttributes(billingProject, workspaceName, Map("a" -> "gs://firecloud-alerts-dev/alerts.json"))
        withWebDriver { implicit driver =>
          withSignIn(user) { listPage =>
            val detailPage = listPage.enterWorkspace(billingProject, workspaceName)
            val previewModal = detailPage.clickForPreview("gs://firecloud-alerts-dev/alerts.json")

            eventually { previewModal.getBucket shouldBe "firecloud-alerts-dev" }
            eventually { previewModal.getObject shouldBe "alerts.json" }

            val filePreview = previewModal.getFilePreview
            //file sometimes changes but is always a JSON array, so easy test...
            filePreview should startWith("[")
            filePreview should endWith("]")
            eventually { previewModal.getPreviewMessage shouldBe "Previews may not be supported for some filetypes." }

            previewModal.xOut()
          }
        }
      }
    }
  }

  "Preview Modal should display correct message when file not previewable" in {
    val user = UserPool.chooseStudent
    implicit val authToken: AuthToken = user.makeAuthToken()
    val bucket = "firecloud-tcga-protected-dummy-dev"
    val gObject = "HCC1143.100_gene_250bp_pad.bam"
    withCleanBillingProject(user) { billingProject =>
      withWorkspace(billingProject, "WorkspaceSpec_gcs_file_not_previewable") { workspaceName =>
        //this is a known file in our bucket
        api.workspaces.setAttributes(billingProject, workspaceName, Map("a" -> s"gs://$bucket/$gObject"))
        withWebDriver { implicit driver =>
          withSignIn(user) { listPage =>
            val detailPage = listPage.enterWorkspace(billingProject, workspaceName)
            val previewModal = detailPage.clickForPreview(s"gs://$bucket/$gObject")

            eventually { previewModal.getBucket shouldBe bucket }
            eventually { previewModal.getObject shouldBe gObject }
            eventually { previewModal.getPreviewMessage shouldBe "Preview is not supported for this filetype." }

            previewModal.xOut()
          }
        }
      }
    }
  }

  "Preview Modal should display correct message and preview when file is accessible for dos:// link" in {
    val user = UserPool.chooseStudent
    implicit val authToken: AuthToken = user.makeAuthToken()
    val dosLink = "dos://broad-dsp-dos.storage.googleapis.com/preview_dos.json"
    val bucket = "broad-dsp-dos"
    val gObject = "dos_test.txt"
    withCleanBillingProject(user) { billingProject =>
      withWorkspace(billingProject, "WorkspaceSpec_dos_file_previewable") { workspaceName =>
        api.workspaces.setAttributes(billingProject, workspaceName, Map("a" -> dosLink))
        withWebDriver { implicit driver =>
          withSignIn(user) { listPage =>
            val detailPage = listPage.enterWorkspace(billingProject, workspaceName)
            val previewModal = detailPage.clickForPreview(dosLink)
            eventually { previewModal.getBucket shouldBe s"$bucket" }
            eventually { previewModal.getObject shouldBe s"$gObject" }
            // preview pane is only created if there's something to preview so
            // give it .1 sec
            retry[Boolean](100.milliseconds, 1.minute)({
              val previewPane = previewModal.findInner("preview-pane")
              // avoids org.openqa.selenium.NoSuchElementException thrown from previewPane.webElement call
              val allElem = previewPane.findAllElements
              if (allElem.nonEmpty && allElem.toList.headOption.get.isDisplayed)
                Some(true)
              else None
            }) match {
              case None => fail()
              case Some(s) => s shouldBe true
            }
            val previewPane = previewModal.findInner("preview-pane")
            //file sometimes changes but is always a JSON array, so easy test...
            eventually { previewPane.webElement.getText shouldBe "This file is for test purposes." }
            eventually { previewModal.getPreviewMessage shouldBe "Previews may not be supported for some filetypes." }

            previewModal.xOut()
          }
        }
      }
    }
  }

  "Preview Modal should display correct message when file not previewable for dos:// link" in {
    val user = UserPool.chooseStudent
    implicit val authToken: AuthToken = user.makeAuthToken()
    val bucket = "broad-public-datasets"
    val gObject = "NA12878_downsampled_for_testing/unmapped/H06JUADXX130110.1.ATCACGAT.20k_reads.bam"
    withCleanBillingProject(user) { billingProject =>
      withWorkspace(billingProject, "WorkspaceSpec_dos_file_not_previewable") { workspaceName =>
        //this is a known file in our bucket
        api.workspaces.setAttributes(billingProject, workspaceName, Map("a" -> dosLink))
        withWebDriver { implicit driver =>
          withSignIn(user) { listPage =>
            val detailPage = listPage.enterWorkspace(billingProject, workspaceName)
            val previewModal = detailPage.clickForPreview(dosLink)
            eventually { previewModal.getBucket shouldBe s"$bucket" }
            eventually { previewModal.getObject shouldBe s"$gObject" }
            eventually { previewModal.getPreviewMessage shouldBe "Preview is not supported for this filetype." }

            previewModal.xOut()
          }
        }
      }
    }
  }

}
