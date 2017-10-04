package org.broadinstitute.dsde.firecloud.test.methodrepo

import org.broadinstitute.dsde.firecloud.config.{AuthToken, AuthTokens, Config}
import org.broadinstitute.dsde.firecloud.fixture.{MethodData, UserFixtures, MethodFixtures}
import org.broadinstitute.dsde.firecloud.page.components.Table
import org.broadinstitute.dsde.firecloud.page.methodrepo.{CreateMethodModal, MethodRepoPage}
import org.broadinstitute.dsde.firecloud.test.{CleanUp, WebBrowserSpec}
import org.scalatest._
import org.broadinstitute.dsde.firecloud.test.Tags


class MethodRepoSpec extends FreeSpec with MethodFixtures with UserFixtures with WebBrowserSpec with Matchers with CleanUp {

  implicit val authToken: AuthToken = AuthTokens.hermione

  "A user" - {
    "should be able to create a method and see it in the table" in withWebDriver { implicit driver =>
      withSignIn(Config.Users.hermione) { _ =>
        val methodRepoPage = new MethodRepoPage()
        val methodRepoTable = methodRepoPage.ui.MethodRepoTable
        methodRepoPage.open
        methodRepoPage.ui.clickNewMethodButton()

        // create it
        val createDialog = new CreateMethodModal()
        val name = "TEST-CREATE-" + randomUuid
        val attributes = MethodData.SimpleMethod.creationAttributes + ("name" -> name)
        val namespace = attributes("namespace")
        createDialog.createMethod(attributes)
        register cleanUp api.methods.redact(namespace, name, 1)

        // wait for it to finish, then go back to the method repo page
        createDialog.awaitDismissed()
        methodRepoPage.open

        // verify that it's in the table
        methodRepoTable.goToTab("My Methods")
        methodRepoTable.filter(name)

        methodRepoTable.hasMethod(namespace, name) shouldBe true
      }
    }

    "should be able to redact a method that they own" in withWebDriver { implicit driver =>
      withMethod("TEST-REDACT-") { case (name, namespace) =>
        withSignIn(Config.Users.hermione) { _ =>
          val methodRepoPage = new MethodRepoPage()
          val methodRepoTable = methodRepoPage.ui.MethodRepoTable
          methodRepoPage.open

          // verify that it's in the table
          methodRepoTable.goToTab("My Methods")
          methodRepoTable.filter(name)

          methodRepoTable.hasMethod(namespace, name) shouldBe true

      // go in and redact it
      val methodDetailPage = methodRepoTable.enterMethod(namespace, name)

      methodDetailPage.ui.redact()

          // and verify that it's gone
          methodRepoPage.open
          methodRepoTable.hasMethod(namespace, name) shouldBe false
        }

      }

    }
  }
}
