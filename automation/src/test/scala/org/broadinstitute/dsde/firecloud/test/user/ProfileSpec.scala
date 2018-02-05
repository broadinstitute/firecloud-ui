package org.broadinstitute.dsde.firecloud.test.user

import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.firecloud.page.user.ProfilePage
import org.broadinstitute.dsde.workbench.config.UserPool
import org.broadinstitute.dsde.workbench.service.test.WebBrowserSpec
import org.scalatest.{FreeSpec, Matchers, Ignore}

@Ignore
class ProfileSpec extends FreeSpec with WebBrowserSpec with UserFixtures with Matchers {

  "Profile page" - {
    "should show the user's proxy group" in withWebDriver { implicit driver =>
      val user = UserPool.chooseStudent
      withSignIn(user) { _ =>
        val profilePage = new ProfilePage().open

        val username = user.email.split("@").head
        profilePage.readProxyGroupEmail should (startWith (username) and endWith ("firecloud.org"))
      }
    }
  }
}
