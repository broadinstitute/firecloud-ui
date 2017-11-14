package org.broadinstitute.dsde.firecloud.test.api.sam

import org.broadinstitute.dsde.firecloud.api.Sam
import org.broadinstitute.dsde.firecloud.api.Sam.user.UserStatus
import org.broadinstitute.dsde.firecloud.auth.{AuthToken, ServiceAccountAuthToken, UserAuthToken}
import org.broadinstitute.dsde.firecloud.config.{Credentials, UserPool}
import org.scalatest.{FreeSpec, Matchers}

class SamApiSpec extends FreeSpec with Matchers {
  val anyUser: Credentials = UserPool.chooseAnyUser
  val userAuthToken: AuthToken = UserAuthToken(anyUser)

  "Sam" - {
    "should give pets the same access as their owners" in {

      // set auth tokens explicitly to control which credentials are used

      val userInfo: UserStatus = Sam.user.status()(userAuthToken)

      val petAccountEmail = Sam.user.petServiceAccountEmail()(userAuthToken)

      // first call should create pet.  confirm that a second call to create/retrieve gives the same results
      Sam.user.petServiceAccountEmail()(userAuthToken) shouldBe petAccountEmail

      val petAuthToken: AuthToken = ServiceAccountAuthToken(petAccountEmail)

      Sam.user.status()(petAuthToken) shouldBe userInfo

      // who is my pet -> who is my user's pet -> it's me
      Sam.user.petServiceAccountEmail()(petAuthToken) shouldBe petAccountEmail
    }
  }
}