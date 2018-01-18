package org.broadinstitute.dsde.firecloud.test.user

import org.broadinstitute.dsde.workbench.config.UserPool
import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.firecloud.page.user.ProfilePage
import org.broadinstitute.dsde.firecloud.test.WebBrowserSpec
import org.scalatest.{FreeSpec, Matchers}

class ProfileSpec extends FreeSpec with WebBrowserSpec with UserFixtures with Matchers {

  "Profile page" - {
    "should show the user's proxy group" in withWebDriver { implicit driver =>
      val user = UserPool.chooseStudent
      withSignIn(user) { _ =>
        val profilePage = new ProfilePage().open

        val username = user.email.split("@").head
        // This is the check we will need in the very near future when we change the formula for making proxy group emails
        //profilePage.readProxyGroupEmail should (startWith (username) and endWith ("firecloud.org"))
        // For now, this is the correct check
        profilePage.readProxyGroupEmail should (startWith ("PROXY_") and endWith ("firecloud.org"))
      }
    }
  }
}
