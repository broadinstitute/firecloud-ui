package org.broadinstitute.dsde.firecloud.test.user

import org.broadinstitute.dsde.firecloud.config.UserPool
import org.broadinstitute.dsde.firecloud.test.{CleanUp, WebBrowserSpec}
import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.scalatest._


class SignInSpec extends FreeSpec with WebBrowserSpec with UserFixtures with CleanUp with Matchers {

  "A user" - {
    "with a registered account" - {

      "should be able to log in and out multiple times as multiple users" in withWebDriver { implicit driver =>
        val users = UserPool.chooseStudents(2)
        val user1 = users.head
        val user2 = users(1)
        withSignInReal(user1) { listPage =>
          listPage.readUserEmail() shouldEqual user1.email
        }
        withSignInReal(user2) { listPage =>
          listPage.readUserEmail() shouldEqual user2.email
        }
        withSignInReal(user1) { listPage =>
          listPage.readUserEmail() shouldEqual user1.email
        }
      }
    }

  }
}
