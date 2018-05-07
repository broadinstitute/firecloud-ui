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
import org.scalatest.{FreeSpec, Matchers, Outcome, fixture}

import scala.io.Source

class DataDownloadSpec extends UnitSpec with WebBrowserSpec with UserFixtures with WorkspaceFixtures
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

  "import a first file" in { f =>
    withWorkspace(f.billingProject, "TestSpec_FireCloud_import_first_file_") { workspaceName =>
      withWebDriver { implicit driver =>
        withSignIn(owner) { _ =>
          val filename = "src/test/resources/participants.txt"
          val workspaceDataTab = new WorkspaceDataPage(f.billingProject, workspaceName).open
          workspaceDataTab.importFile(filename)
          workspaceDataTab.getNumberOfParticipants shouldBe 1
          logger.info("messge")
          info("info here")
        }
      }
    }
  }

  "import a second file" in { f =>
    withWorkspace(f.billingProject, "TestSpec_FireCloud_import_second_file_") { workspaceName =>
      withWebDriver { implicit driver =>
        withSignIn(owner) { _ =>
          val filename = "src/test/resources/participants.txt"
          val workspaceDataTab = new WorkspaceDataPage(f.billingProject, workspaceName).open
          workspaceDataTab.importFile(filename)
          workspaceDataTab.getNumberOfParticipants shouldBe 1
        }
      }
    }
  }

}
