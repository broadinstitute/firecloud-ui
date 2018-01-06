package org.broadinstitute.dsde.firecloud.test.methodrepo

import org.broadinstitute.dsde.firecloud.auth.{AuthToken, UserAuthToken}
import org.broadinstitute.dsde.firecloud.config.{Credentials, UserPool}
import org.broadinstitute.dsde.firecloud.fixture.{MethodData, MethodFixtures, UserFixtures}
import org.broadinstitute.dsde.firecloud.page.methodrepo.{MethodRepoPage, MethodRepoTable}
import org.broadinstitute.dsde.firecloud.test.{CleanUp, WebBrowserSpec}
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
        val attributes = MethodData.SimpleMethod.creationAttributes + ("name" -> name)
        val namespace = attributes("namespace")
        methodRepoPage.createNewMethod(attributes)
        register cleanUp api.methods.redact(namespace, name, 1)

        // go back to the method repo page and verify that it's in the table
        methodRepoPage.open
        val methodTable = new MethodRepoTable()
        methodTable.goToTab("My Methods")
        methodTable.filter(name)

        methodTable.hasMethod(namespace, name) shouldBe true
      }
    }

    "should be able to redact a method that they own" in withWebDriver { implicit driver =>
      withMethod( "TEST-REDACT-" ) { case (name,namespace)=>
        withSignIn(ownerUser) { workspaceListPage =>
          val methodRepoPage = workspaceListPage.goToMethodRepository()

          // verify that it's in the table
          val methodTable = new MethodRepoTable()
          methodTable.goToTab("My Methods")
          methodTable.filter(name)

          methodTable.hasMethod(namespace, name) shouldBe true

          // go in and redact it
          val methodDetailPage = methodTable.enterMethod(namespace, name)

          methodDetailPage.redact()

          // and verify that it's gone
          methodDetailPage.goToMethodRepository()
          methodTable.hasMethod(namespace, name) shouldBe false
        }
      }
    }
  }
}
