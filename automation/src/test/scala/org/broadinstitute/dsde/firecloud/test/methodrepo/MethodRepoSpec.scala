package org.broadinstitute.dsde.firecloud.test.methodrepo

import org.broadinstitute.dsde.firecloud.config.{AuthToken, AuthTokens, Config}
import org.broadinstitute.dsde.firecloud.fixture.MethodData
import org.broadinstitute.dsde.firecloud.page.Table
import org.broadinstitute.dsde.firecloud.page.methodrepo.{CreateMethodModal, MethodRepoPage}
import org.broadinstitute.dsde.firecloud.test.WebBrowserSpec
import org.scalatest._

class MethodRepoSpec extends FreeSpec with WebBrowserSpec with Matchers {

  implicit val authToken: AuthToken = AuthTokens.harry

  "A user" - {
    "should be able to create a method, see it, and then redact it" in withWebDriver { implicit driver =>
      signIn(Config.Users.hermione)
      val methodRepoPage = new MethodRepoPage()
      methodRepoPage.open
      methodRepoPage.ui.clickNewMethodButton()

      // create it
      val createDialog = new CreateMethodModal()
      val name = "TEST-METHOD-REPO-" + randomUuid
      val attributes = MethodData.SimpleMethod.creationAttributes + ("name" -> name)
      val namespace = attributes("namespace")
      createDialog.createMethod(attributes)

      // wait for it to finish, then go back to the method repo page
      createDialog.awaitDismissed()
      methodRepoPage.open

      // verify that it's in the table
      val methodsTable = new Table("methods-table")
      methodsTable.goToTab("My Methods")
      methodsTable.filter(name)

      methodRepoPage.ui.hasMethod(namespace, name) shouldBe true

      // go back into it and redact
      val methodDetailPage = methodRepoPage.ui.enterMethod(namespace, name)
      methodDetailPage.awaitLoaded()
      methodDetailPage.ui.redact()

      methodRepoPage.open
      methodRepoPage.ui.hasMethod(namespace, name) shouldBe false
    }
  }
}
