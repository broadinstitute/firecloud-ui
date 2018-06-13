package org.broadinstitute.dsde.firecloud.test.user

import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.workbench.config.UserPool
import org.broadinstitute.dsde.workbench.service.test.WebBrowserSpec
import org.scalatest._
import org.scalatest.time.{Millis, Seconds, Span}


class SignInSpec extends FreeSpec with WebBrowserSpec with UserFixtures with Matchers {

  implicit override val patienceConfig = PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(500, Millis)))

  "A user" - {
    "with a registered account" - {

      "should be able to log in and out multiple times as multiple users" in {
        val Seq(user1, user2) = UserPool.chooseStudents(2)
        withWebDriver { implicit driver =>
          withSignInReal(user1) { listPage =>
            eventually {
              listPage.readUserEmail() shouldEqual user1.email
            }
          }
        }
        withWebDriver { implicit driver =>
          withSignInReal(user2) { listPage =>
            eventually { listPage.readUserEmail() shouldEqual user2.email }
          }
        }
      }
    }
  }
}
