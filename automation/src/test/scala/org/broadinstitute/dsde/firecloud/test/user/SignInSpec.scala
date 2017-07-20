package org.broadinstitute.dsde.firecloud.test.user

import org.broadinstitute.dsde.firecloud.config.{AuthToken, AuthTokens, Config}
import org.broadinstitute.dsde.firecloud.test.{CleanUp, WebBrowserSpec}
import org.scalatest._

class SignInSpec extends FreeSpec with WebBrowserSpec with CleanUp {

  implicit val authToken: AuthToken = AuthTokens.harry

  "A user" - {
    "with a registered account" - {

      "should be able to log in and out multiple times as multiple users" in withWebDriver { implicit driver =>
        var listPageAsUser1 = signIn(Config.Users.harry)
        listPageAsUser1.signOut()
        val listPageAsUser2 = signIn(Config.Users.ron)
        assert(listPageAsUser2.readUserEmail().startsWith(Config.Users.ron.email))
        listPageAsUser2.signOut()
        listPageAsUser1 = signIn(Config.Users.harry)
        assert(listPageAsUser1.readUserEmail().startsWith(Config.Users.harry.email))
      }
    }

  }
}
