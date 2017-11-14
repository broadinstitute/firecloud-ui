package org.broadinstitute.dsde.firecloud.test.api

import org.broadinstitute.dsde.firecloud.config.{AuthToken, Credentials, UserPool}
import org.scalatest.{FreeSpec, Matchers}

class LeonardoSpec extends FreeSpec with Matchers {
  val anyUser: Credentials = UserPool.chooseAnyUser
  implicit val authToken: AuthToken = AuthToken(anyUser)

  "Leonardo" - {
    "should ping" in {
      Leonardo.test.ping() shouldBe "OK"
    }
  }
}