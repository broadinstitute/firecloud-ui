package org.broadinstitute.dsde.firecloud.test.metadata

import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceDataPage
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.UserPool

class DataDownloadSpec extends MetaDataSpecBase {

  "Download should reflect visible columns" - {
    "no workspace defaults or user preferences" in withWebDriver(downloadPath) { implicit driver =>
      testMetadataDownload(
        initialColumns = List("participant_id", "foo"),
        expectedColumns = List("participant_id", "foo"))
    }

    "no workspace defaults, with user preferences" in withWebDriver(downloadPath) { implicit driver =>
      testMetadataDownload(
        initialColumns = List("participant_id", "foo"),
        userHidden = Some("foo"),
        expectedColumns = List("participant_id"))
    }

    "no workspace defaults, with user preferences, new columns" in withWebDriver(downloadPath) { implicit driver =>
      testMetadataDownload(
        initialColumns = List("participant_id", "foo"),
        userHidden = Some("foo"),
        importColumns = Some(List("participant_id", "foo", "bar")),
        expectedColumns = List("participant_id", "bar"))
    }

    "with workspace defaults, no user preferences" in withWebDriver(downloadPath) { implicit driver =>
      testMetadataDownload(
        initialColumns = List("participant_id", "foo", "bar"),
        defaultShown = Some(List("participant_id", "foo")),
        defaultHidden = Some(List("bar")),
        expectedColumns = List("participant_id", "foo"))
    }

    "with workspace defaults, no user preferences, new columns" in withWebDriver(downloadPath) { implicit driver =>
      testMetadataDownload(
        initialColumns = List("participant_id", "foo", "bar"),
        defaultShown = Some(List("participant_id", "foo")),
        defaultHidden = Some(List("bar")),
        importColumns = Some(List("participant_id", "foo", "bar", "baz")),
        expectedColumns = List("participant_id", "foo", "baz"))
    }

    "with workspace defaults, with user preferences" in withWebDriver(downloadPath) { implicit driver =>
      testMetadataDownload(
        initialColumns = List("participant_id", "foo", "bar"),
        defaultShown = Some(List("participant_id", "foo")),
        defaultHidden = Some(List("bar")),
        userHidden = Some("foo"),
        expectedColumns = List("participant_id"))
    }

    "with workspace defaults, with user preferences, new columns" in withWebDriver(downloadPath) { implicit driver =>
      testMetadataDownload(
        initialColumns = List("participant_id", "foo", "bar"),
        defaultShown = Some(List("participant_id", "foo")),
        defaultHidden = Some(List("bar")),
        userHidden = Some("foo"),
        importColumns = Some(List("participant_id", "foo", "bar", "baz")),
        expectedColumns = List("participant_id", "baz"))
    }

    "keep ID column in download even if hidden in UI" in withWebDriver(downloadPath) { implicit driver =>
      val user = UserPool.chooseAnyUser
      implicit val authToken: AuthToken = user.makeAuthToken()
      withWorkspace(billingProject, "DataSpec_download") { workspaceName =>
        withSignIn(user) { _ =>
          val dataTab = new WorkspaceDataPage(billingProject, workspaceName).open
          val columns = List("participant_id", "foo")
          dataTab.importFile(generateMetadataFile(columns))
          dataTab.dataTable.hideColumn("participant_id")
          val metadataFile = dataTab.downloadMetadata(Option(downloadPath)).get
          readHeadersFromTSV(metadataFile) shouldEqual columnsToFileHeaders(columns)
        }
      }
    }
  }

}
