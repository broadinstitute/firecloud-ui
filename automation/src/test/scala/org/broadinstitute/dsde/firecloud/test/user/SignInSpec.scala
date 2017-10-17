package org.broadinstitute.dsde.firecloud.test.user

import org.broadinstitute.dsde.firecloud.config.Config
import org.broadinstitute.dsde.firecloud.test.{CleanUp, WebBrowserSpec}
import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.scalatest._
import org.broadinstitute.dsde.firecloud.test.Tags

class SignInSpec extends FreeSpec with WebBrowserSpec with UserFixtures with CleanUp with Matchers {

  "A user" - {
    "with a registered account" - {

      "should be able to log in and out multiple times as multiple users" in withWebDriver { implicit driver =>
        withSignIn(Config.Users.draco) { listPage =>
          listPage.readUserEmail() shouldEqual Config.Users.draco.email
        }
        withSignIn(Config.Users.snape) { listPage =>
          listPage.readUserEmail() shouldEqual Config.Users.snape.email
        }
        withSignIn(Config.Users.draco) { listPage =>
          listPage.readUserEmail() shouldEqual Config.Users.draco.email

        }
      }
    }

  }
}
