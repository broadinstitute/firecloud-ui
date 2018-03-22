package org.broadinstitute.dsde.firecloud.test.metadata

import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceDataPage
import org.broadinstitute.dsde.firecloud.page.workspaces.summary.WorkspaceSummaryPage
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.UserPool
import org.broadinstitute.dsde.workbench.service.{AclEntry, WorkspaceAccessLevel}

class DataImportSpec extends MetaDataSpecBase {


  "Writer and reader should see new columns" - {
    "With no defaults or local preferences when writer imports metadata with new column" in withWebDriver { implicit driver =>
      val owner = UserPool.chooseProjectOwner
      val reader = UserPool.chooseStudent
      implicit val authToken: AuthToken = owner.makeAuthToken()
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

    "With local preferences, but no defaults when writer imports metadata with new column" in withWebDriver { implicit driver =>
      val owner = UserPool.chooseProjectOwner
      val reader = UserPool.chooseStudent
      implicit val authToken: AuthToken = owner.makeAuthToken()
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

    "With defaults on workspace, but no local preferences when writer imports metadata with new column" in withWebDriver { implicit driver =>
      val owner = UserPool.chooseProjectOwner
      val reader = UserPool.chooseStudent
      implicit val authToken: AuthToken = owner.makeAuthToken()
      withWorkspace(billingProject, "DataSpec_col_display_w_defaults", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) {
        workspaceName =>

          val headers1 = List("participant_id", "test1", "test2", "test3")
          val headers2 = headers1 :+ "test4"

          withSignIn(owner) { _ =>
            val workspaceSummaryTab = new WorkspaceSummaryPage(billingProject, workspaceName).open
            workspaceSummaryTab.edit {
              workspaceSummaryTab.addWorkspaceAttribute("workspace-column-defaults", "{\"participant\": {\"shown\": [\"participant_id\", \"test1\"], \"hidden\": [\"test2\", \"test3\"]}}")
            }
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

    "With defaults on workspace and local preferences for reader and writer when writer imports metadata with new column" in withWebDriver { implicit driver =>
      val owner = UserPool.chooseProjectOwner
      val reader = UserPool.chooseStudent
      implicit val authToken: AuthToken = owner.makeAuthToken()
      withWorkspace(billingProject, "DataSpec_col_display_w_defaults_and_local", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) { workspaceName =>

        val headers1 = List("participant_id", "test1", "test2", "test3", "test4")
        val headers2 = headers1 :+ "test5"

        withSignIn(owner) { _ =>
          val workspaceSummaryTab = new WorkspaceSummaryPage(billingProject, workspaceName).open
          workspaceSummaryTab.edit {
            workspaceSummaryTab.addWorkspaceAttribute("workspace-column-defaults", "{\"participant\": {\"shown\": [\"participant_id\", \"test1\", \"test4\"], \"hidden\": [\"test2\", \"test3\"]}}")
          }
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
