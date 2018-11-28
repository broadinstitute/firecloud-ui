package org.broadinstitute.dsde.firecloud.test.user

import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.firecloud.page.user.ProfilePage
import org.broadinstitute.dsde.workbench.auth.AuthTokenScopes
import org.broadinstitute.dsde.workbench.config.UserPool
import org.broadinstitute.dsde.workbench.fixture.TestReporterFixture
import org.broadinstitute.dsde.workbench.service.test.WebBrowserSpec
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FreeSpec, Matchers}

class ProfileSpec extends FreeSpec with WebBrowserSpec with UserFixtures with Matchers with Eventually with TestReporterFixture {

  implicit override val patienceConfig = PatienceConfig(timeout = scaled(Span(10, Seconds)), interval = scaled(Span(500, Millis)))

  "Profile page" - {
    "should show the user's proxy group" in withWebDriver { implicit driver =>
      val user = UserPool.chooseStudent
      withSignIn(user) { _ =>
        val profilePage = new ProfilePage().open

        val username = user.email.split("@").head
        /* Re-enable this code and remove the temporary code below after fixing rawls for GAWB-2933
        profilePage.readProxyGroupEmail should (startWith (username) and endWith ("firecloud.org"))
        */
        eventually { profilePage.readProxyGroupEmail should endWith ("firecloud.org") }

      }
    }

    "should link with fence and dcf-fence" in withWebDriver { implicit driver =>
      val user = UserPool.chooseStudent
      withSignIn(user) { _ =>
        val profilePage = new ProfilePage().open
        val scopes = AuthTokenScopes.userLoginScopes.toList

        profilePage.linkFence
        executeScript(s"window.forceSignedIn('${user.makeAuthToken(scopes).value}')")
        profilePage.linkDcfFence
        executeScript(s"window.forceSignedIn('${user.makeAuthToken(scopes).value}')")

        eventually { findAll(linkText("Log-In to Framework Services to re-link your account")).size shouldBe 2 }
      }
    }
  }
}
