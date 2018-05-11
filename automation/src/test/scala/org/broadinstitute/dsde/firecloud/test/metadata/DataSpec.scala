package org.broadinstitute.dsde.firecloud.test.metadata

import org.broadinstitute.dsde.workbench.config.UserPool
import java.io.{File, PrintWriter}

import org.broadinstitute.dsde.firecloud.fixture.TestData
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceDataPage
import org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs.WorkspaceMethodConfigDetailsPage
import org.broadinstitute.dsde.firecloud.fixture.UISpec
import org.broadinstitute.dsde.workbench.fixture._
import org.broadinstitute.dsde.workbench.service.{AclEntry, WorkspaceAccessLevel}
import org.broadinstitute.dsde.workbench.service.test.{CleanUp, RandomUtil}

class DataSpec extends UISpec with CleanUp {

  val methodConfigName: String = randomIdWithPrefix(SimpleMethodConfig.configName)

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
    "with no defaults or local preferences when analysis run that creates new columns" in { projectOwnerFixture =>
      val reader = UserPool.chooseStudent
      withWorkspace(projectOwnerFixture.billingProject, "TestSpec_FireCloud_launch_a_simple_workflow", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
        api.importMetaData(projectOwnerFixture.billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)
        api.methodConfigurations.copyMethodConfigFromMethodRepo(projectOwnerFixture.billingProject, workspaceName, SimpleMethodConfig.configNamespace,
          SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, SimpleMethodConfig.configNamespace, methodConfigName)

        withWebDriver { implicit driver =>
          withSignIn(owner) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(projectOwnerFixture.billingProject, workspaceName).open
            val headers1 = List("participant_id")
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual headers1
            api.workspaces.waitForBucketReadAccess(projectOwnerFixture.billingProject, workspaceName)
            val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(projectOwnerFixture.billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open
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
            val workspaceDataTab = new WorkspaceDataPage(projectOwnerFixture.billingProject, workspaceName).open
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "output")
          }
        }
      }
    }

    "with local preferences but no defaults when analysis run" in { projectOwnerFixture =>
      val reader = UserPool.chooseStudent
      withWorkspace(projectOwnerFixture.billingProject, "DataSpec_launchAnalysis_local", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
        api.importMetaData(projectOwnerFixture.billingProject, workspaceName, "entities", "entity:participant_id\ttest1\ttest2\nparticipant1\t1\t2")
        api.methodConfigurations.copyMethodConfigFromMethodRepo(projectOwnerFixture.billingProject, workspaceName, SimpleMethodConfig.configNamespace,
          SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, SimpleMethodConfig.configNamespace, methodConfigName)

        withWebDriver { implicit driver =>
          withSignIn(owner) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(projectOwnerFixture.billingProject, workspaceName).open
            workspaceDataTab.dataTable.hideColumn("test1")
          }
          withSignIn(reader) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(projectOwnerFixture.billingProject, workspaceName).open
            workspaceDataTab.dataTable.hideColumn("test2")
          }
          withSignIn(owner) { _ =>
            api.workspaces.waitForBucketReadAccess(projectOwnerFixture.billingProject, workspaceName)
            val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(projectOwnerFixture.billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open
            val submissionTab = methodConfigDetailsPage.launchAnalysis(SimpleMethodConfig.rootEntityType, TestData.SingleParticipant.entityId, "", true)
            submissionTab.waitUntilSubmissionCompletes()
            assert(submissionTab.verifyWorkflowSucceeded())
            val workspaceDataTab = new WorkspaceDataPage(projectOwnerFixture.billingProject, workspaceName).open
            workspaceDataTab.dataTable.clearFilter()
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test2", "output")
          }
          withSignIn(reader) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(projectOwnerFixture.billingProject, workspaceName).open
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "output")
          }
        }
      }
    }

    "with defaults but no local preferences when analysis run" in { projectOwnerFixture =>
      val reader = UserPool.chooseStudent
      withWorkspace(projectOwnerFixture.billingProject, "DataSpec_launchAnalysis_defaults", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
        api.importMetaData(projectOwnerFixture.billingProject, workspaceName, "entities", "entity:participant_id\ttest1\ttest2\nparticipant1\t1\t2")
        api.methodConfigurations.copyMethodConfigFromMethodRepo(projectOwnerFixture.billingProject, workspaceName, SimpleMethodConfig.configNamespace,
          SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, SimpleMethodConfig.configNamespace, methodConfigName)

        api.workspaces.setAttributes(projectOwnerFixture.billingProject, workspaceName, Map("workspace-column-defaults" -> "{\"participant\": {\"shown\": [\"participant_id\", \"test1\"], \"hidden\": [\"test2\"]}}"))

        withWebDriver { implicit driver =>
          withSignIn(owner) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(projectOwnerFixture.billingProject, workspaceName).open
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1")
          }
          withSignIn(reader) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(projectOwnerFixture.billingProject, workspaceName).open
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1")
          }
          withSignIn(owner) { _ =>
            api.workspaces.waitForBucketReadAccess(projectOwnerFixture.billingProject, workspaceName)
            val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(projectOwnerFixture.billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open
            val submissionTab = methodConfigDetailsPage.launchAnalysis(SimpleMethodConfig.rootEntityType, TestData.SingleParticipant.entityId, "", true)
            submissionTab.waitUntilSubmissionCompletes()
            assert(submissionTab.verifyWorkflowSucceeded())
            val workspaceDataTab = new WorkspaceDataPage(projectOwnerFixture.billingProject, workspaceName).open
            workspaceDataTab.dataTable.clearFilter()
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "output")
          }
          withSignIn(reader) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(projectOwnerFixture.billingProject, workspaceName).open
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "output")
          }
        }
      }
    }

    "with defaults and local preferences when analysis is run" in { billingFixture =>
      val reader = UserPool.chooseStudent
      withWorkspace(billingFixture.billingProject, "DataSpec_localDefaults_analysis", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
        api.importMetaData(billingFixture.billingProject, workspaceName, "entities", "entity:participant_id\ttest1\ttest2\ttest3\nparticipant1\t1\t2\t3")
        api.methodConfigurations.copyMethodConfigFromMethodRepo(billingFixture.billingProject, workspaceName, SimpleMethodConfig.configNamespace,
          SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, SimpleMethodConfig.configNamespace, methodConfigName)
        api.workspaces.setAttributes(billingFixture.billingProject, workspaceName, Map("workspace-column-defaults" -> "{\"participant\": {\"shown\": [\"participant_id\", \"test1\", \"test3\"], \"hidden\": [\"test2\"]}}"))

        withWebDriver { implicit driver =>
          withSignIn(owner) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(billingFixture.billingProject, workspaceName).open
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "test3")
            workspaceDataTab.dataTable.hideColumn("test1")
          }
          withSignIn(reader) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(billingFixture.billingProject, workspaceName).open
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "test3")
            workspaceDataTab.dataTable.hideColumn("test3")
          }
          withSignIn(owner) { _ =>
            api.workspaces.waitForBucketReadAccess(billingFixture.billingProject, workspaceName)
            val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingFixture.billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open
            val submissionTab = methodConfigDetailsPage.launchAnalysis(SimpleMethodConfig.rootEntityType, TestData.SingleParticipant.entityId, "", true)
            submissionTab.waitUntilSubmissionCompletes()
            assert(submissionTab.verifyWorkflowSucceeded())
            val workspaceDataTab = new WorkspaceDataPage(billingFixture.billingProject, workspaceName).open
            workspaceDataTab.dataTable.clearFilter()
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test3", "output")
          }
          withSignIn(reader) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(billingFixture.billingProject, workspaceName).open
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "output")
          }
        }
      }
    }
  }

  "Writer and reader should see new columns" - {
    "With no defaults or local preferences when writer imports metadata with new column" in { billingFixture =>
      val reader = UserPool.chooseStudent
      withWorkspace(billingFixture.billingProject, "DataSpec_column_display", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) { workspaceName =>

        val headers1 = List("participant_id", "test1")
        val headers2 = headers1 :+ "test2"

        withWebDriver { implicit driver =>
          withSignIn(owner) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(billingFixture.billingProject, workspaceName).open
            createAndImportMetadataFile(headers1, workspaceDataTab)
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual headers1
            createAndImportMetadataFile(headers2, workspaceDataTab)
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual headers2
          }
          withSignIn(reader) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(billingFixture.billingProject, workspaceName).open
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual headers2
          }
        }
      }
    }

    "With local preferences, but no defaults when writer imports metadata with new column" in { billingFixture =>
      val reader = UserPool.chooseStudent
      withWorkspace(billingFixture.billingProject, "DataSpec_col_display_w_preferences", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) { workspaceName =>

        val headers1 = List("participant_id", "test1", "test2")
        val headers2 = List("participant_id", "test1", "test2", "test3")

        withWebDriver { implicit driver =>
          withSignIn(owner) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(billingFixture.billingProject, workspaceName).open
            createAndImportMetadataFile(headers1, workspaceDataTab)
            workspaceDataTab.dataTable.hideColumn("test1")
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test2")
          }
          withSignIn(reader) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(billingFixture.billingProject, workspaceName).open
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual headers1
            workspaceDataTab.dataTable.hideColumn("test2")
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1")
          }
          withSignIn(owner) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(billingFixture.billingProject, workspaceName).open
            createAndImportMetadataFile(headers2, workspaceDataTab)
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test2", "test3")
          }
          withSignIn(reader) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(billingFixture.billingProject, workspaceName).open
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "test3")
          }
        }
      }
    }

    "With defaults on workspace, but no local preferences when writer imports metadata with new column" in { billingFixture =>
      val reader = UserPool.chooseStudent
      withWorkspace(billingFixture.billingProject, "DataSpec_col_display_w_defaults", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) {
        workspaceName =>

          val headers1 = List("participant_id", "test1", "test2", "test3")
          val headers2 = headers1 :+ "test4"
          api.workspaces.setAttributes(billingFixture.billingProject, workspaceName, Map("workspace-column-defaults" -> "{\"participant\": {\"shown\": [\"participant_id\", \"test1\"], \"hidden\": [\"test2\", \"test3\"]}}"))

          withWebDriver { implicit driver =>
            withSignIn(owner) { _ =>
              val workspaceDataTab = new WorkspaceDataPage(billingFixture.billingProject, workspaceName).open
              createAndImportMetadataFile(headers1, workspaceDataTab)
              workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1")
            }
            withSignIn(reader) { _ =>
              val workspaceDataTab = new WorkspaceDataPage(billingFixture.billingProject, workspaceName).open
              workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1")
            }
            withSignIn(owner) { _ =>
              val workspaceDataTab = new WorkspaceDataPage(billingFixture.billingProject, workspaceName).open
              createAndImportMetadataFile(headers2, workspaceDataTab)
              workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "test4")
            }
            withSignIn(reader) { _ =>
              val workspaceDataTab = new WorkspaceDataPage(billingFixture.billingProject, workspaceName).open
              workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "test4")
            }
          }
      }
    }

    "With defaults on workspace and local preferences for reader and writer when writer imports metadata with new column" in { billingFixture =>
      val reader = UserPool.chooseStudent
      withWorkspace(billingFixture.billingProject, "DataSpec_col_display_w_defaults_and_local", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) { workspaceName =>

        val headers1 = List("participant_id", "test1", "test2", "test3", "test4")
        val headers2 = headers1 :+ "test5"
        api.workspaces.setAttributes(billingFixture.billingProject, workspaceName, Map("workspace-column-defaults" -> "{\"participant\": {\"shown\": [\"participant_id\", \"test1\", \"test4\"], \"hidden\": [\"test2\", \"test3\"]}}"))

        withWebDriver { implicit driver =>
          withSignIn(owner) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(billingFixture.billingProject, workspaceName).open
            createAndImportMetadataFile(headers1, workspaceDataTab)
            workspaceDataTab.dataTable.hideColumn("test1")
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test4")
          }
          withSignIn(reader) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(billingFixture.billingProject, workspaceName).open
            workspaceDataTab.dataTable.hideColumn("test4")
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1")
          }
          withSignIn(owner) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(billingFixture.billingProject, workspaceName).open
            createAndImportMetadataFile(headers2, workspaceDataTab)
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test4", "test5")
          }
          withSignIn(reader) { _ =>
            val workspaceDataTab = new WorkspaceDataPage(billingFixture.billingProject, workspaceName).open
            workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "test5")
          }
        }
      }
    }
  }


  //  This test is just to make sure functionality in this context works
  //  BUT we should really also write some tests for this specific component (seperate of this context)
  "Column reordering should be reflected" in { billingFixture =>
    withWorkspace(billingFixture.billingProject, "DataSpec_reordercolumns") { workspaceName =>
      api.importMetaData(billingFixture.billingProject, workspaceName, "entities", "entity:participant_id\ttest1\ttest2\ttest3\nparticipant1\t1\t2\t3")

      withWebDriver { implicit driver =>
        withSignIn(owner) { _ =>
          val workspaceDataTab = new WorkspaceDataPage(billingFixture.billingProject, workspaceName).open
          workspaceDataTab.dataTable.moveColumn("test1", "test3")
          workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test2", "test3", "test1")
        }
      }
    }
  }

}