package org.broadinstitute.dsde.firecloud.test.metadata

import java.io.{File, PrintWriter}

import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceDataPage
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.UserPool
import org.broadinstitute.dsde.workbench.fixture._
import org.broadinstitute.dsde.workbench.service.test.{CleanUp, WebBrowserSpec, WebBrowserUtil}
import org.broadinstitute.dsde.workbench.service.{AclEntry, WorkspaceAccessLevel}
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.WebBrowser
import org.scalatest.{FreeSpec, Matchers}

import scala.io.Source

class DataDownloadSpec extends FreeSpec with WebBrowserSpec with UserFixtures with WorkspaceFixtures with BillingFixtures
  with Matchers with WebBrowser with WebBrowserUtil with CleanUp {

  private def makeTempDownloadDirectory(): String = {
    /*
     * This might work some day if docker permissions get straightened out... or it might not be
     * needed. For now, we instead `chmod 777` the directory in run-tests.sh.
    new File("chrome").mkdirs()
    val downloadPath = Files.createTempDirectory(Paths.get("chrome"), "downloads")
    val permissions = Set(PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_WRITE, PosixFilePermission.OTHERS_WRITE)
    Files.setPosixFilePermissions(downloadPath, permissions.asJava)
    downloadPath.toString
     */
    val downloadPath = "chrome/downloads"
    new File(downloadPath).mkdirs()
    downloadPath
  }

  private val downloadPath = makeTempDownloadDirectory()

  "import a participants file" in {
    val owner = UserPool.chooseProjectOwner
    implicit val authToken: AuthToken = owner.makeAuthToken()
    withCleanBillingProject(owner) { billingProject =>
      withWorkspace(billingProject, "TestSpec_FireCloud_import_participants_file_") { workspaceName =>

        withWebDriver { implicit driver =>
          withSignIn(owner) { _ =>
            val filename = "src/test/resources/participants.txt"
            val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
            workspaceDataTab.importFile(filename)
            workspaceDataTab.getNumberOfParticipants shouldBe 1
          }
        }
      }
    }
  }

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
      withCleanBillingProject(user) { billingProject =>
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


  private def testMetadataDownload(initialColumns: List[String],
                                   defaultShown: Option[List[String]] = None,
                                   defaultHidden: Option[List[String]] = None,
                                   userHidden: Option[String] = None,
                                   importColumns: Option[List[String]] = None,
                                   expectedColumns: List[String])
                                  (implicit webDriver: WebDriver): Unit = {
    val owner = UserPool.chooseProjectOwner
    val writer = UserPool.chooseStudent
    implicit val authToken: AuthToken = owner.makeAuthToken()

    withCleanBillingProject(owner) { billingProject =>
      withWorkspace(billingProject, "DataSpec_download", aclEntries = List(AclEntry(writer.email, WorkspaceAccessLevel.Writer))) { workspaceName =>
        withSignIn(owner) { _ =>
          (defaultShown, defaultHidden) match {
            case (None, None) => Unit
            case (_, _) => setColumnDefaults(billingProject, workspaceName, defaultShown.getOrElse(List()), defaultHidden.getOrElse(List()))
          }

          val dataTab = new WorkspaceDataPage(billingProject, workspaceName).open
          dataTab.importFile(generateMetadataFile(initialColumns))
          userHidden.foreach(dataTab.dataTable.hideColumn)
        }

        withSignIn(writer) { _ =>
          val dataTab = new WorkspaceDataPage(billingProject, workspaceName).open
          userHidden.foreach(dataTab.dataTable.hideColumn)

          importColumns foreach { l => dataTab.importFile(generateMetadataFile(l)) }

          dataTab.dataTable.readColumnHeaders shouldEqual expectedColumns
          val metadataFile = dataTab.downloadMetadata(Option(downloadPath)).get
          readHeadersFromTSV(metadataFile) shouldEqual columnsToFileHeaders(expectedColumns)
        }

        withSignIn(owner) { _ =>
          val dataTab = new WorkspaceDataPage(billingProject, workspaceName).open
          dataTab.dataTable.readColumnHeaders shouldEqual expectedColumns
          val metadataFile = dataTab.downloadMetadata(Option(downloadPath)).get
          readHeadersFromTSV(metadataFile) shouldEqual columnsToFileHeaders(expectedColumns)
        }
      }
    }
  }

  private def generateMetadataFile(headers: List[String]) = {
    val data = for {
      h <- headers
    } yield {
      if (h == "participant_id") {
        "participant1"
      } else {
        h.takeRight(1)
      }
    }
    makeMetadataFile("DataSpec_column_display", headers, List(data))
  }

  private def makeMetadataFile(filePrefix: String, headers: List[String], rows: List[List[String]]): File = {
    val metadataFile = File.createTempFile(filePrefix, "txt")
    val writer = new PrintWriter(metadataFile)
    val rowStrings = rows map { _.mkString(s"\t") }
    val fileContent = s"""entity:${headers.mkString(s"\t")}\n${rowStrings.mkString(s"\n")}"""
    writer.write(fileContent)
    writer.close()
    metadataFile
  }

  private def setColumnDefaults(billingProject: String, workspaceName: String, shown: List[String], hidden: List[String])
                       (implicit authToken: AuthToken): Unit = {

    def buildColumnDefaults: String = {
      def join(columns: List[String]) = columns match {
        case List() => ""
        case _ => "\"" + s"${columns.mkString("\", \"")}" + "\""
      }
      s"""{"participant": {"shown": [${join(shown)}], "hidden": [${join(hidden)}]}}"""
    }

    api.workspaces.setAttributes(billingProject, workspaceName, Map("workspace-column-defaults" -> buildColumnDefaults))
  }

  private def readHeadersFromTSV(fileName: String): List[String] = {
    Source.fromFile(fileName).getLines().next().split('\t').toList
  }

  private def columnsToFileHeaders(columns: List[String]): List[String] = {
    List("entity:" + columns.head) ++ columns.tail
  }
}
