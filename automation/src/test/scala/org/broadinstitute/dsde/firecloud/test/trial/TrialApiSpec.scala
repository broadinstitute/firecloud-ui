package org.broadinstitute.dsde.firecloud.test.trial

import org.broadinstitute.dsde.firecloud.api.Sam.user.{UserStatus, UserStatusDetails}
import org.broadinstitute.dsde.firecloud.api.{Orchestration, Sam, Thurloe}
import org.broadinstitute.dsde.firecloud.auth.AuthToken
import org.broadinstitute.dsde.firecloud.config.{Credentials, UserPool}
import org.broadinstitute.dsde.workbench.model._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{FreeSpec, Matchers}

final class TrialApiSpec extends FreeSpec with Matchers with ScalaFutures {
  implicit override val patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(5, Seconds)))
  val adminAuthToken = UserPool.chooseAdmin.makeAuthToken()

  def registerAsNewUser(email: WorkbenchEmail)(implicit authToken: AuthToken): Unit = {
    val newUserProfile = Orchestration.profile.BasicProfile (
      firstName = "Enroller",
      lastName = "FreeTrial",
      title = "User",
      contactEmail = Option(email.value),
      institute = "Broad",
      institutionalProgram = "DSP",
      programLocationCity = "Cambridge",
      programLocationState = "MA",
      programLocationCountry = "USA",
      pi = "Albus Dumbledore",
      nonProfitStatus = "true"
    )
    Orchestration.profile.registerUser(newUserProfile)
  }

  // TODO: Factor out if continued to be used
  def removeUser(subjectId: String): Unit = {
    implicit val token: AuthToken = adminAuthToken

    if (Sam.admin.doesUserExist(subjectId).getOrElse(false)) {
      Sam.admin.deleteUser(subjectId)
    }
    Thurloe.keyValuePairs.deleteAll(subjectId)
  }

  "Free Trial" - {
    "should be idempotent for user status cycle" in {

      // Pick a temporary non-existent user
      val tempUser: Credentials = UserPool.chooseTemp
      val tempAuthToken: AuthToken = tempUser.makeAuthToken()
//      Sam.user.status()(tempAuthToken) shouldBe None

      // Register the user
//      registerAsNewUser(WorkbenchEmail(tempUser.email))(tempAuthToken)

      // Verify the user is registered
//    // val tempUserStatus = Sam.user.status()(tempAuthToken)
      val tempUserStatus = Some(UserStatus(UserStatusDetails("111010567286567716739", "luna.temp@test.firecloud.org"), Map("ldap" -> true, "allUsersGroup" -> true, "google" -> true)))
      val tempUserInfo = tempUserStatus.get.userInfo
      val tempUserEmail = tempUserInfo.userEmail
      val tempUserSubjectId = tempUserInfo.userSubjectId
//    tempUserEmail shouldBe tempUser.email

      // TODO: Verify that user's trial Thurloe KVPs don't exist

      // Enable the user
       Orchestration.trial.enableUsers(Seq(tempUserEmail))(adminAuthToken)

      // Verify the user is enabled
      Thurloe.keyValuePairs.getAll(tempUserSubjectId)(adminAuthToken)("trialState") shouldBe "Enabled"

      // TODO: Verify the user is enrolled with the expected record

      // TODO: Have the user enrolled

      // TODO: Verify the user is enrolled with the expected record

      // Remove the user
//      removeUser(tempUserSubjectId)
//      Sam.user.status()(tempAuthToken) shouldBe None
    }
  }
}
