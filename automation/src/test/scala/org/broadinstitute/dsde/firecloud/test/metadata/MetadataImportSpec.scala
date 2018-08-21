package org.broadinstitute.dsde.firecloud.test.metadata

import java.io.{File, PrintWriter}

import org.broadinstitute.dsde.firecloud.fixture.{TestData, UserFixtures}
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceDataPage
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.UserPool
import org.broadinstitute.dsde.workbench.fixture._
import org.broadinstitute.dsde.workbench.service.test.WebBrowserSpec
import org.broadinstitute.dsde.workbench.service.{AclEntry, WorkspaceAccessLevel}
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FreeSpec, Matchers, ParallelTestExecution}


class MetadataImportSpec extends FreeSpec with ParallelTestExecution with WebBrowserSpec with UserFixtures with WorkspaceFixtures
  with BillingFixtures with Matchers with TestReporterFixture {

  override implicit val patienceConfig = PatienceConfig(timeout = scaled(Span(10, Seconds)), interval = scaled(Span(500, Millis)))

  val methodConfigName: String = randomIdWithPrefix(SimpleMethodConfig.configName)
  val testData = TestData()

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

    "With no defaults or local preferences when writer imports metadata with new column" in {
      val owner = UserPool.chooseProjectOwner
      val reader = UserPool.chooseStudent
      implicit val authToken: AuthToken = owner.makeAuthToken()
      withCleanBillingProject(owner) { billingProject =>
        withWorkspace(billingProject, "DataSpec_column_display", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) { workspaceName =>

          val headers1 = List("participant_id", "test1")
          val headers2 = headers1 :+ "test2"

          withWebDriver { implicit driver =>
            withSignIn(owner) { _ =>
              val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
              createAndImportMetadataFile(headers1, workspaceDataTab)
              eventually { workspaceDataTab.dataTable.readColumnHeaders shouldEqual headers1 }
              createAndImportMetadataFile(headers2, workspaceDataTab)
                eventually { workspaceDataTab.dataTable.readColumnHeaders shouldEqual headers2 }
            }
            withSignIn(reader) { _ =>
              val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
              eventually { workspaceDataTab.dataTable.readColumnHeaders shouldEqual headers2 }
            }
          }
        }
      }
    }

    "With local preferences, but no defaults when writer imports metadata with new column" in {
      val owner = UserPool.chooseProjectOwner
      val reader = UserPool.chooseStudent
      implicit val authToken: AuthToken = owner.makeAuthToken()
      withCleanBillingProject(owner) { billingProject =>
        withWorkspace(billingProject, "DataSpec_col_display_w_preferences", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) { workspaceName =>

          val headers1 = List("participant_id", "test1", "test2")
          val headers2 = List("participant_id", "test1", "test2", "test3")

          withWebDriver { implicit driver =>
            withSignIn(owner) { _ =>
              val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
              createAndImportMetadataFile(headers1, workspaceDataTab)
              workspaceDataTab.dataTable.hideColumn("test1")
              eventually { workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test2") }
            }
            withSignIn(reader) { _ =>
              val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
              eventually { workspaceDataTab.dataTable.readColumnHeaders shouldEqual headers1 }
              workspaceDataTab.dataTable.hideColumn("test2")
              eventually { workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1") }
            }
            withSignIn(owner) { _ =>
              val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
              createAndImportMetadataFile(headers2, workspaceDataTab)
              eventually { workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test2", "test3") }
            }
            withSignIn(reader) { _ =>
              val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
              eventually { workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "test3") }
            }
          }
        }
      }
    }

    "With defaults on workspace, but no local preferences when writer imports metadata with new column" in {
      val owner = UserPool.chooseProjectOwner
      val reader = UserPool.chooseStudent
      implicit val authToken: AuthToken = owner.makeAuthToken()
      withCleanBillingProject(owner) { billingProject =>
        withWorkspace(billingProject, "DataSpec_col_display_w_defaults", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) {
          workspaceName =>

            val headers1 = List("participant_id", "test1", "test2", "test3")
            val headers2 = headers1 :+ "test4"
            api.workspaces.setAttributes(billingProject, workspaceName, Map("workspace-column-defaults" -> "{\"participant\": {\"shown\": [\"participant_id\", \"test1\"], \"hidden\": [\"test2\", \"test3\"]}}"))

            withWebDriver { implicit driver =>
              withSignIn(owner) { _ =>
                val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
                createAndImportMetadataFile(headers1, workspaceDataTab)
                eventually { workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1") }
              }
              withSignIn(reader) { _ =>
                val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
                eventually { workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1") }
              }
              withSignIn(owner) { _ =>
                val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
                createAndImportMetadataFile(headers2, workspaceDataTab)
                eventually { workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "test4") }
              }
              withSignIn(reader) { _ =>
                val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
                eventually { workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "test4") }
              }
            }
        }
      }
    }

    "With defaults on workspace and local preferences for reader and writer when writer imports metadata with new column" in {
      val owner = UserPool.chooseProjectOwner
      val reader = UserPool.chooseStudent
      implicit val authToken: AuthToken = owner.makeAuthToken()
      withCleanBillingProject(owner) { billingProject =>
        withWorkspace(billingProject, "DataSpec_col_display_w_defaults_and_local", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) { workspaceName =>

          val headers1 = List("participant_id", "test1", "test2", "test3", "test4")
          val headers2 = headers1 :+ "test5"
          api.workspaces.setAttributes(billingProject, workspaceName, Map("workspace-column-defaults" -> "{\"participant\": {\"shown\": [\"participant_id\", \"test1\", \"test4\"], \"hidden\": [\"test2\", \"test3\"]}}"))

          withWebDriver { implicit driver =>
            withSignIn(owner) { _ =>
              val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
              createAndImportMetadataFile(headers1, workspaceDataTab)
              workspaceDataTab.dataTable.hideColumn("test1")
              eventually { workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test4") }
            }
            withSignIn(reader) { _ =>
              val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
              workspaceDataTab.dataTable.hideColumn("test4")
              eventually { workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1") }
            }
            withSignIn(owner) { _ =>
              val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
              createAndImportMetadataFile(headers2, workspaceDataTab)
              eventually { workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test4", "test5") }
            }
            withSignIn(reader) { _ =>
              val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
              eventually { workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "test5") }
            }
          }
        }
      }
    }
  }


  //  This test is just to make sure functionality in this context works
  //  BUT we should really also write some tests for this specific component (seperate of this context)
  "Column reordering should be reflected" in {
    val user = UserPool.chooseProjectOwner
    implicit val authToken: AuthToken = user.makeAuthToken()
    withCleanBillingProject(user) { billingProject =>
      withWorkspace(billingProject, "DataSpec_reordercolumns") { workspaceName =>
        api.importMetaData(billingProject, workspaceName, "entities", "entity:participant_id\ttest1\ttest2\ttest3\nparticipant1\t1\t2\t3")

        withWebDriver { implicit driver =>
          withSignIn(user) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
            workspaceDataTab.dataTable.moveColumn("test1", "test3")
            eventually { workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test2", "test3", "test1") }
          }
        }
      }
    }
  }

}
