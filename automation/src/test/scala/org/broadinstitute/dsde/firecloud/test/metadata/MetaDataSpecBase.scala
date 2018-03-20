package org.broadinstitute.dsde.firecloud.test.metadata

import java.io.{File, PrintWriter}

import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceDataPage
import org.broadinstitute.dsde.firecloud.page.workspaces.summary.WorkspaceSummaryPage
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{Config, UserPool}
import org.broadinstitute.dsde.workbench.fixture.WorkspaceFixtures
import org.broadinstitute.dsde.workbench.service.{AclEntry, WorkspaceAccessLevel}
import org.broadinstitute.dsde.workbench.service.test.{CleanUp, WebBrowserSpec, WebBrowserUtil}
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.WebBrowser
import org.scalatest.{FreeSpec, Matchers}

import scala.io.Source

abstract class MetaDataSpecBase extends FreeSpec with WebBrowserSpec with UserFixtures with CleanUp
    with WorkspaceFixtures with Matchers with WebBrowser with WebBrowserUtil{

  protected val downloadPath = makeTempDownloadDirectory()
  protected val billingProject = Config.Projects.default

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

  protected def generateMetadataFile(headers: List[String]) = {
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

  protected def testMetadataDownload(initialColumns: List[String],
                                   defaultShown: Option[List[String]] = None,
                                   defaultHidden: Option[List[String]] = None,
                                   userHidden: Option[String] = None,
                                   importColumns: Option[List[String]] = None,
                                   expectedColumns: List[String])
                                  (implicit webDriver: WebDriver): Unit = {
    val owner = UserPool.chooseProjectOwner
    val writer = UserPool.chooseStudent
    implicit val authToken: AuthToken = owner.makeAuthToken()

    withWorkspace(billingProject, "DataSpec_download", aclEntries = List(AclEntry(writer.email, WorkspaceAccessLevel.Writer))) { workspaceName =>
      withSignIn(owner) { _ =>
        (defaultShown, defaultHidden) match {
          case (None, None) => Unit
          case (_, _) => setColumnDefaults(workspaceName, defaultShown.getOrElse(List()), defaultHidden.getOrElse(List()))
        }

        val dataTab = new WorkspaceDataPage(Config.Projects.default, workspaceName).open
        dataTab.importFile(generateMetadataFile(initialColumns))
        userHidden.foreach(dataTab.dataTable.hideColumn)
      }

      withSignIn(writer) { _ =>
        val dataTab = new WorkspaceDataPage(Config.Projects.default, workspaceName).open
        userHidden.foreach(dataTab.dataTable.hideColumn)

        importColumns foreach { l => dataTab.importFile(generateMetadataFile(l)) }

        dataTab.dataTable.readColumnHeaders shouldEqual expectedColumns
        val metadataFile = dataTab.downloadMetadata(Option(downloadPath)).get
        readHeadersFromTSV(metadataFile) shouldEqual columnsToFileHeaders(expectedColumns)
      }

      withSignIn(owner) { _ =>
        val dataTab = new WorkspaceDataPage(Config.Projects.default, workspaceName).open
        dataTab.dataTable.readColumnHeaders shouldEqual expectedColumns
        val metadataFile = dataTab.downloadMetadata(Option(downloadPath)).get
        readHeadersFromTSV(metadataFile) shouldEqual columnsToFileHeaders(expectedColumns)
      }
    }
  }


  def makeMetadataFile(filePrefix: String, headers: List[String], rows: List[List[String]]): File = {
    val metadataFile = File.createTempFile(filePrefix, "txt")
    val writer = new PrintWriter(metadataFile)
    val rowStrings = rows map { _.mkString(s"\t") }
    val fileContent = s"""entity:${headers.mkString(s"\t")}\n${rowStrings.mkString(s"\n")}"""
    writer.write(fileContent)
    writer.close()
    metadataFile
  }

  def setColumnDefaults(workspaceName: String, shown: List[String], hidden: List[String])
                       (implicit webDriver: WebDriver): Unit = {
    val workspaceSummaryTab = new WorkspaceSummaryPage(Config.Projects.default, workspaceName).open
    workspaceSummaryTab.edit {
      workspaceSummaryTab.addWorkspaceAttribute("workspace-column-defaults", buildColumnDefaults)
    }

    def buildColumnDefaults: String = {
      def join(columns: List[String]) = columns match {
        case List() => ""
        case l => "\"" + s"${columns.mkString("\", \"")}" + "\""
      }
      s"""{"participant": {"shown": [${join(shown)}], "hidden": [${join(hidden)}]}}"""
    }
  }
  def readHeadersFromTSV(fileName: String): List[String] = {
    Source.fromFile(fileName).getLines().next().split('\t').toList
  }

  def columnsToFileHeaders(columns: List[String]): List[String] = {
    List("entity:" + columns.head) ++ columns.tail
  }

  def createAndImportMetadataFile(headers: List[String], dataTab: WorkspaceDataPage): Unit = {
    val file: File = generateMetadataFile(headers)
    dataTab.importFile(file.getAbsolutePath)
  }

}
