package org.broadinstitute.dsde.firecloud.test.methodrepo

import org.broadinstitute.dsde.firecloud.config.{AuthToken, AuthTokens, Config}
import org.broadinstitute.dsde.firecloud.fixture.MethodData
import org.broadinstitute.dsde.firecloud.page.methodrepo.{CreateMethodModal, MethodRepoPage}
import org.broadinstitute.dsde.firecloud.test.WebBrowserSpec
import org.scalatest.FreeSpec

class MethodRepoSpec extends FreeSpec with WebBrowserSpec {

  implicit val authToken: AuthToken = AuthTokens.harry

  "A user" - {
    "should be able to create a method" in withWebDriver { implicit driver =>
      signIn(Config.Users.harry)
      val page = new MethodRepoPage()
      page.open
      page.ui.clickNewMethodButton()

      val createDialog = new CreateMethodModal()
      createDialog.createMethod(MethodData.SimpleMethod.creationAttributes)

      // TODO: verify it worked, clean up
    }
  }
}
