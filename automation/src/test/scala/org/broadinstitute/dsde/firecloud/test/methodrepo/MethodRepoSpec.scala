package org.broadinstitute.dsde.firecloud.test.methodrepo

import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.firecloud.page.methodrepo.MethodRepoPage
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{Credentials, UserPool}
import org.broadinstitute.dsde.workbench.fixture.{BillingFixtures, MethodData, MethodFixtures, WorkspaceFixtures}
import org.broadinstitute.dsde.workbench.service.test.{CleanUp, WebBrowserSpec}
import org.scalatest._


class MethodRepoSpec extends FreeSpec with MethodFixtures with UserFixtures with WorkspaceFixtures with BillingFixtures
  with WebBrowserSpec with Matchers with CleanUp {

  val ownerUser: Credentials = UserPool.chooseProjectOwner
  implicit val ownerAuthToken: AuthToken = ownerUser.makeAuthToken()

  "A user" - {
    "should be able to create a method and see it in the table" in {
      withCleanUp {
        withWebDriver { implicit driver =>
          withSignIn(ownerUser) { _ =>
            val methodRepoPage = new MethodRepoPage().open

            // create it
            val name = "TEST-CREATE-" + randomUuid
            val attributes = MethodData.SimpleMethod.creationAttributes + ("name" -> name) + ("documentation" -> "documentation")
            val namespace = attributes("namespace")
            methodRepoPage.createNewMethod(attributes)
            register cleanUp api.methods.redact(namespace, name, 1)

            // go back to the method repo page and verify that it's in the table
            methodRepoPage.open
            methodRepoPage.methodRepoTable.goToTab("My Methods")
            methodRepoPage.methodRepoTable.filter(name)

            methodRepoPage.methodRepoTable.hasMethod(namespace, name) shouldBe true
          }
        }
      }
    }

    "should be able to redact a method that they own" in {
      withMethod( "TEST-REDACT" ) { case (name,namespace)=>
        withWebDriver { implicit driver =>
          withSignIn(ownerUser) { workspaceListPage =>
            val methodRepoPage = workspaceListPage.goToMethodRepository()

            // verify that it's in the table
            methodRepoPage.methodRepoTable.goToTab("My Methods")
            methodRepoPage.methodRepoTable.filter(name)

            methodRepoPage.methodRepoTable.hasMethod(namespace, name) shouldBe true

            // go in and redact it
            val methodDetailPage = methodRepoPage.methodRepoTable.enterMethod(namespace, name)

            methodDetailPage.redact()

            // and verify that it's gone
            methodDetailPage.goToMethodRepository()
            methodRepoPage.methodRepoTable.hasMethod(namespace, name) shouldBe false
          }
        }
      }
    }

    "should be able to export a method" - {
      "to an existing workspace" in {
        withMethod("TEST-EXPORT") { case (methodName, methodNamespace) =>
          withCleanBillingProject(ownerUser) { billingProject =>
            withWorkspace(billingProject, "TEST-EXPORT-DESTINATION") { workspaceName =>
              withWebDriver { implicit driver =>
                withSignIn(ownerUser) { workspaceListPage =>
                  val methodRepoPage = workspaceListPage.goToMethodRepository()

                  methodRepoPage.methodRepoTable.goToTab("My Methods")
                  val exportModal = methodRepoPage.methodRepoTable.enterMethod(methodNamespace, methodName).startExport()
                  val finalPage = exportModal.firstPage.useBlankConfiguration()

                  finalPage.workspaceSelector.selectExisting(billingProject, workspaceName)
                  finalPage.confirm()

                  val detailsPage = exportModal.getPostExportModal.goToWorkspace(billingProject, workspaceName)
                  detailsPage.isLoaded shouldBe true
                }
              }
            }
          }
        }
      }

      "to a new workspace" in {
        withMethod("TEST-EXPORT") { case (methodName, methodNamespace) =>
          withCleanBillingProject(ownerUser) { billingProject =>
            withCleanUp {
              withWebDriver { implicit driver =>
                withSignIn(ownerUser) { workspaceListPage =>
                  val methodRepoPage = workspaceListPage.goToMethodRepository()

                  methodRepoPage.methodRepoTable.goToTab("My Methods")
                  val exportModal = methodRepoPage.methodRepoTable.enterMethod(methodNamespace, methodName).startExport()
                  val finalPage = exportModal.firstPage.useBlankConfiguration()

                  val workspaceName = "test_create_on_export_" + randomUuid
                  finalPage.workspaceSelector.selectNew(billingProject, workspaceName)
                  finalPage.confirm()
                  // register cleanUp after workspace created cleanly. otherwise, cleanup throws exception
                  register cleanUp api.workspaces.delete(billingProject, workspaceName)

                  val detailsPage = exportModal.getPostExportModal.goToWorkspace(billingProject, workspaceName)
                  detailsPage.isLoaded shouldBe true
                }
              }
            }
          }
        }
      }
    }
  }
}
