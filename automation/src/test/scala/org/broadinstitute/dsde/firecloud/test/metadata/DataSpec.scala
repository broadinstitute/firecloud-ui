package org.broadinstitute.dsde.firecloud.test.metadata

import org.broadinstitute.dsde.workbench.config.{Config, UserPool}
import java.io.{File, PrintWriter}
import java.util.UUID

import org.broadinstitute.dsde.firecloud.fixture.{TestData, UserFixtures}
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceDataPage
import org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs.WorkspaceMethodConfigDetailsPage
import org.broadinstitute.dsde.firecloud.page.workspaces.summary.WorkspaceSummaryPage
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.fixture._
import org.broadinstitute.dsde.workbench.service.{AclEntry, Orchestration, WorkspaceAccessLevel}
import org.broadinstitute.dsde.workbench.service.test.{CleanUp, WebBrowserSpec, WebBrowserUtil}
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.WebBrowser
import org.scalatest.{FreeSpec, Matchers}

import scala.io.Source

class DataSpec extends FreeSpec with WebBrowserSpec
  with UserFixtures with WorkspaceFixtures with BillingFixtures
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
//  val owner: Credentials = UserPool.chooseProjectOwner
//  val reader: Credentials = UserPool.chooseStudent
//  implicit lazy val authToken: AuthToken = owner.makeAuthToken()

  "import a participants file" in withWebDriver { implicit driver =>
    val owner = UserPool.chooseProjectOwner
    implicit val authToken: AuthToken = owner.makeAuthToken()
    withCleanBillingProject(owner) { billingProject =>
      withWorkspace(billingProject, "TestSpec_FireCloud_import_participants_file_") { workspaceName =>
        withSignIn(owner) { _ =>
          val filename = "src/test/resources/participants.txt"
          val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
          workspaceDataTab.importFile(filename)
          workspaceDataTab.getNumberOfParticipants shouldBe 1
        }
      }
    }
  }

  val configNs = SimpleMethodConfig.configNamespace
  val configName = SimpleMethodConfig.configName
  val methodName: String = MethodData.SimpleMethod.methodName + "_" + UUID.randomUUID().toString
  val methodConfigName: String = SimpleMethodConfig.configName + "_" + UUID.randomUUID().toString

  def makeMetadataFile(filePrefix: String, headers: List[String], rows: List[List[String]]): File = {
    val metadataFile = File.createTempFile(filePrefix, "txt")
    val writer = new PrintWriter(metadataFile)
    val rowStrings = rows map { _.mkString(s"\t") }
    val fileContent = s"""entity:${headers.mkString(s"\t")}\n${rowStrings.mkString(s"\n")}"""
    writer.write(fileContent)
    writer.close()
    metadataFile
  }

  def createAndImportMetadataFile(headers: List[String], dataTab: WorkspaceDataPage): Unit = {
    val file: File = generateMetadataFile(headers)
    dataTab.importFile(file.getAbsolutePath)
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

  "Writer and reader should see new columns" - {
    "with no defaults or local preferences when analysis run that creates new columns" in withWebDriver { implicit driver =>
      val owner = UserPool.chooseProjectOwner
      val reader = UserPool.chooseStudent
      implicit val authToken: AuthToken = owner.makeAuthToken()
      withCleanBillingProject(owner) { billingProject =>
        withWorkspace(billingProject, "TestSpec_FireCloud_launch_a_simple_workflow", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
          api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)
          api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, SimpleMethodConfig.configNamespace,
            SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, SimpleMethodConfig.configNamespace, methodConfigName)

          withSignIn(owner) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
            val headers1 = List("participant_id")
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual headers1
            api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)
            val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open
            val submissionTab = methodConfigDetailsPage.launchAnalysis(SimpleMethodConfig.rootEntityType, TestData.SingleParticipant.entityId, "", true)
            submissionTab.waitUntilSubmissionCompletes()
            assert(submissionTab.verifyWorkflowSucceeded())
            workspaceDataTab.open
            //there is at least one filter bug - possibly two that was breaking the tests
            //1) Not sure if bug or not: filter from launch analysis modal is still present when data tab revisited
            //2) Filter on the datatab removes even the row being referenced
            //this clear filter fixes the problem. Can be removed when filter bug fixed
            workspaceDataTab.dataTable.clearFilter()
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "output")
          }
          withSignIn(reader) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "output")
          }
        }
      }
    }

    "with local preferences but no defaults when analysis run" in withWebDriver { implicit driver =>
      val owner = UserPool.chooseProjectOwner
      val reader = UserPool.chooseStudent
      implicit val authToken: AuthToken = owner.makeAuthToken()
      withCleanBillingProject(owner) { billingProject =>
        withWorkspace(billingProject, "DataSpec_launchAnalysis_local", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
          api.importMetaData(billingProject, workspaceName, "entities", "entity:participant_id\ttest1\ttest2\nparticipant1\t1\t2")
          api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, SimpleMethodConfig.configNamespace,
            SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, SimpleMethodConfig.configNamespace, methodConfigName)

          withSignIn(owner) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
            workspaceDataTab.dataTable.hideColumn("test1")
          }
          withSignIn(reader) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
            workspaceDataTab.dataTable.hideColumn("test2")
          }
          withSignIn(owner) { _ =>
            api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)
            val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open
            val submissionTab = methodConfigDetailsPage.launchAnalysis(SimpleMethodConfig.rootEntityType, TestData.SingleParticipant.entityId, "", true)
            submissionTab.waitUntilSubmissionCompletes()
            assert(submissionTab.verifyWorkflowSucceeded())
            val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
            workspaceDataTab.dataTable.clearFilter()
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test2", "output")
          }
          withSignIn(reader) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "output")
          }
        }
      }
    }

    "with defaults but no local preferences when analysis run" in withWebDriver { implicit driver =>
      val owner = UserPool.chooseProjectOwner
      val reader = UserPool.chooseStudent
      implicit val authToken: AuthToken = owner.makeAuthToken()
      withCleanBillingProject(owner) { billingProject =>
        withWorkspace(billingProject, "DataSpec_launchAnalysis_defaults", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
          api.importMetaData(billingProject, workspaceName, "entities", "entity:participant_id\ttest1\ttest2\nparticipant1\t1\t2")
          api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, SimpleMethodConfig.configNamespace,
            SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, SimpleMethodConfig.configNamespace, methodConfigName)

          api.workspaces.setAttributes(billingProject, workspaceName, Map("workspace-column-defaults" -> "{\"participant\": {\"shown\": [\"participant_id\", \"test1\"], \"hidden\": [\"test2\"]}}"))
          withSignIn(owner) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1")
          }
          withSignIn(reader) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1")
          }
          withSignIn(owner) { _ =>
            api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)
            val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open
            val submissionTab = methodConfigDetailsPage.launchAnalysis(SimpleMethodConfig.rootEntityType, TestData.SingleParticipant.entityId, "", true)
            submissionTab.waitUntilSubmissionCompletes()
            assert(submissionTab.verifyWorkflowSucceeded())
            val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
            workspaceDataTab.dataTable.clearFilter()
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "output")
          }
          withSignIn(reader) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "output")
          }
        }
      }
    }
    "with defaults and local preferences when analysis is run" in withWebDriver { implicit driver =>
      val owner = UserPool.chooseProjectOwner
      val reader = UserPool.chooseStudent
      implicit val authToken: AuthToken = owner.makeAuthToken()
      withCleanBillingProject(owner) { billingProject =>
        withWorkspace(billingProject, "DataSpec_localDefaults_analysis", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
          api.importMetaData(billingProject, workspaceName, "entities", "entity:participant_id\ttest1\ttest2\ttest3\nparticipant1\t1\t2\t3")
          api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, SimpleMethodConfig.configNamespace,
            SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, SimpleMethodConfig.configNamespace, methodConfigName)
          api.workspaces.setAttributes(billingProject, workspaceName, Map("workspace-column-defaults" -> "{\"participant\": {\"shown\": [\"participant_id\", \"test1\", \"test3\"], \"hidden\": [\"test2\"]}}"))
          withSignIn(owner) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "test3")
            workspaceDataTab.dataTable.hideColumn("test1")
          }
          withSignIn(reader) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "test3")
            workspaceDataTab.dataTable.hideColumn("test3")
          }
          withSignIn(owner) { _ =>
            api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)
            val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open
            val submissionTab = methodConfigDetailsPage.launchAnalysis(SimpleMethodConfig.rootEntityType, TestData.SingleParticipant.entityId, "", true)
            submissionTab.waitUntilSubmissionCompletes()
            assert(submissionTab.verifyWorkflowSucceeded())
            val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
            workspaceDataTab.dataTable.clearFilter()
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test3", "output")
          }
          withSignIn(reader) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "output")
          }
        }
      }
    }
  }

  "Writer and reader should see new columns" - {
    "With no defaults or local preferences when writer imports metadata with new column" in withWebDriver { implicit driver =>
      val owner = UserPool.chooseProjectOwner
      val reader = UserPool.chooseStudent
      implicit val authToken: AuthToken = owner.makeAuthToken()
      withCleanBillingProject(owner) { billingProject =>
        withWorkspace(billingProject, "DataSpec_column_display", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) { workspaceName =>

          val headers1 = List("participant_id", "test1")
          val headers2 = headers1 :+ "test2"
          withSignIn(owner) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
            createAndImportMetadataFile(headers1, workspaceDataTab)
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual headers1
            createAndImportMetadataFile(headers2, workspaceDataTab)
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual headers2
          }
          withSignIn(reader) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual headers2
          }
        }
      }
    }

    "With local preferences, but no defaults when writer imports metadata with new column" in withWebDriver { implicit driver =>
      val owner = UserPool.chooseProjectOwner
      val reader = UserPool.chooseStudent
      implicit val authToken: AuthToken = owner.makeAuthToken()
      withCleanBillingProject(owner) { billingProject =>
        withWorkspace(billingProject, "DataSpec_col_display_w_preferences", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) { workspaceName =>

          val headers1 = List("participant_id", "test1", "test2")
          val headers2 = List("participant_id", "test1", "test2", "test3")

          withSignIn(owner) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
            createAndImportMetadataFile(headers1, workspaceDataTab)
            workspaceDataTab.dataTable.hideColumn("test1")
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test2")
          }
          withSignIn(reader) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual headers1
            workspaceDataTab.dataTable.hideColumn("test2")
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1")
          }
          withSignIn(owner) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
            createAndImportMetadataFile(headers2, workspaceDataTab)
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test2", "test3")
          }
          withSignIn(reader) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "test3")
          }
        }
      }
    }

    "With defaults on workspace, but no local preferences when writer imports metadata with new column" in withWebDriver { implicit driver =>
      val owner = UserPool.chooseProjectOwner
      val reader = UserPool.chooseStudent
      implicit val authToken: AuthToken = owner.makeAuthToken()
      withCleanBillingProject(owner) { billingProject =>
        withWorkspace(billingProject, "DataSpec_col_display_w_defaults", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) {
          workspaceName =>

            val headers1 = List("participant_id", "test1", "test2", "test3")
            val headers2 = headers1 :+ "test4"
            api.workspaces.setAttributes(billingProject, workspaceName, Map("workspace-column-defaults" -> "{\"participant\": {\"shown\": [\"participant_id\", \"test1\"], \"hidden\": [\"test2\", \"test3\"]}}"))

            withSignIn(owner) { _ =>
              val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
              createAndImportMetadataFile(headers1, workspaceDataTab)
              workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1")
            }
            withSignIn(reader) { _ =>
              val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
              workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1")
            }
            withSignIn(owner) { _ =>
              val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
              createAndImportMetadataFile(headers2, workspaceDataTab)
              workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "test4")
            }
            withSignIn(reader) { _ =>
              val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
              workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "test4")
            }
        }
      }
    }

    "With defaults on workspace and local preferences for reader and writer when writer imports metadata with new column" in withWebDriver { implicit driver =>
      val owner = UserPool.chooseProjectOwner
      val reader = UserPool.chooseStudent
      implicit val authToken: AuthToken = owner.makeAuthToken()
      withCleanBillingProject(owner) { billingProject =>
        withWorkspace(billingProject, "DataSpec_col_display_w_defaults_and_local", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) { workspaceName =>

          val headers1 = List("participant_id", "test1", "test2", "test3", "test4")
          val headers2 = headers1 :+ "test5"
          api.workspaces.setAttributes(billingProject, workspaceName, Map("workspace-column-defaults" -> "{\"participant\": {\"shown\": [\"participant_id\", \"test1\", \"test4\"], \"hidden\": [\"test2\", \"test3\"]}}"))

          withSignIn(owner) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
            createAndImportMetadataFile(headers1, workspaceDataTab)
            workspaceDataTab.dataTable.hideColumn("test1")
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test4")
          }
          withSignIn(reader) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
            workspaceDataTab.dataTable.hideColumn("test4")
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1")
          }
          withSignIn(owner) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
            createAndImportMetadataFile(headers2, workspaceDataTab)
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test4", "test5")
          }
          withSignIn(reader) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "test5")
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

//  This test is just to make sure functionality in this context works
//  BUT we should really also write some tests for this specific component (seperate of this context)
  "Column reordering should be reflected" in withWebDriver {implicit driver =>
    val user = UserPool.chooseProjectOwner
    implicit val authToken: AuthToken = user.makeAuthToken()
    withCleanBillingProject(user) { billingProject =>
      withWorkspace(billingProject, "DataSpec_reordercolumns") { workspaceName =>
        api.importMetaData(billingProject, workspaceName, "entities", "entity:participant_id\ttest1\ttest2\ttest3\nparticipant1\t1\t2\t3")
        withSignIn(user) { _ =>
          val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
          workspaceDataTab.dataTable.moveColumn("test1", "test3")
          workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test2", "test3", "test1")
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

  def setColumnDefaults(billingProject: String, workspaceName: String, shown: List[String], hidden: List[String])
                       (implicit authToken: AuthToken): Unit = {

    def buildColumnDefaults: String = {
      def join(columns: List[String]) = columns match {
        case List() => ""
        case l => "\"" + s"${columns.mkString("\", \"")}" + "\""
      }
      s"""{"participant": {"shown": [${join(shown)}], "hidden": [${join(hidden)}]}}"""
    }

    api.workspaces.setAttributes(billingProject, workspaceName, Map("workspace-column-defaults" -> buildColumnDefaults))
  }
  def readHeadersFromTSV(fileName: String): List[String] = {
    Source.fromFile(fileName).getLines().next().split('\t').toList
  }

  def columnsToFileHeaders(columns: List[String]): List[String] = {
    List("entity:" + columns.head) ++ columns.tail
  }
}
