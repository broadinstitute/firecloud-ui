package org.broadinstitute.dsde.firecloud.test.user

import org.broadinstitute.dsde.firecloud.config.{AuthToken, AuthTokens, Config}
import org.broadinstitute.dsde.firecloud.test.{CleanUp, WebBrowserSpec}
import org.scalatest._
import org.broadinstitute.dsde.firecloud.test.Tags


class SignInSpec extends FreeSpec with WebBrowserSpec with CleanUp {

  implicit val authToken: AuthToken = AuthTokens.draco

  "A user" - {
    "with a registered account" - {

      "should be able to log in and out multiple times as multiple users" taggedAs Tags.GooglePassing in withWebDriver { implicit driver =>
        var listPageAsUser1 = signIn(Config.Users.draco)
        listPageAsUser1.signOut()
        val listPageAsUser2 = signIn(Config.Users.snape)
        assert(listPageAsUser2.readUserEmail().equals(Config.Users.snape.email))
        listPageAsUser2.signOut()
        listPageAsUser1 = signIn(Config.Users.draco)
        assert(listPageAsUser1.readUserEmail().equals(Config.Users.draco.email))
      }
    }

  }
}
