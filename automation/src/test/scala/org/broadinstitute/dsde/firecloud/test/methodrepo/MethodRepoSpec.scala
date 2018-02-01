package org.broadinstitute.dsde.firecloud.test.methodrepo

import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.firecloud.page.methodrepo.MethodRepoPage
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{Credentials, UserPool}
import org.broadinstitute.dsde.workbench.fixture.{MethodData, MethodFixtures}
import org.broadinstitute.dsde.workbench.service.test.{CleanUp, WebBrowserSpec}
import org.scalatest._


class MethodRepoSpec extends FreeSpec with MethodFixtures with UserFixtures with WebBrowserSpec with Matchers with CleanUp {

  val ownerUser: Credentials = UserPool.chooseProjectOwner
  implicit val ownerAuthToken: AuthToken = ownerUser.makeAuthToken()

  "A user" - {
    "should be able to create a method and see it in the table" in withWebDriver { implicit driver =>
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

    "should be able to redact a method that they own" in withWebDriver { implicit driver =>
      withMethod( "TEST-REDACT-" ) { case (name,namespace)=>
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
}
