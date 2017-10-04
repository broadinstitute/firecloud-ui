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
      withSignIn(Config.Users.hermione){ _ =>
      val methodRepoPage = new MethodRepoPage()
      .open


      // create it
      val  name = "TEST-CREATE-" + randomUuid
      val attributes = MethodData.SimpleMethod.creationAttributes + ("name" -> name)
      val namespace = attributes("namespace")
      methodRepoPage.createNewMethod(attributes)
      register cleanUp api.methods.redact(namespace, name, 1)

      // go back to the method repo page
      and verify that it's in the table
      methodRepoPage.openmethodRepoPage.MethodRepoTable.goToTab("My Methods")
      methodRepoPage.MethodRepoTable.filter(name)

      methodRepoPage.MethodRepoTable.hasMethod(namespace, name) shouldBe true}
    }

    "should be able to redact a method that they own" in withWebDriver { implicit driver =>
      withMethod( "TEST-REDACT-" ) { case (name,namespace)=>
      withSignIn(Config.Users.hermione){ _ =>
      val methodRepoPage = new MethodRepoPage()
      .open

      // verify that it's in the table
      methodRepoPage.MethodRepoTable.goToTab("My Methods")
      methodRepoPage.MethodRepoTable.filter(name)

      methodRepoPage.MethodRepoTable.hasMethod(namespace, name) shouldBe true

      // go in and redact it
      val methodDetailPage = methodRepoPage.MethodRepoTable.enterMethod(namespace, name)

      methodDetailPage.redact()

      // and verify that it's gone
      methodRepoPage.open
      methodRepoPage.MethodRepoTable.hasMethod(namespace, name) shouldBe false}

      }
    }
  }
}
