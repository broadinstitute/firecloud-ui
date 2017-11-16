package org.broadinstitute.dsde.firecloud.test.api.sam

import org.broadinstitute.dsde.firecloud.api.{Orchestration, Sam, Thurloe}
import org.broadinstitute.dsde.firecloud.api.Sam.user.UserStatusDetails
import org.broadinstitute.dsde.firecloud.auth.{AuthToken, ServiceAccountAuthToken}
import org.broadinstitute.dsde.firecloud.config.{Config, Credentials, UserPool}
import org.broadinstitute.dsde.workbench.model.{WorkbenchUserServiceAccount, WorkbenchUserServiceAccountEmail, WorkbenchUserServiceAccountName, WorkbenchUserServiceAccountSubjectId}
import org.broadinstitute.dsde.firecloud.dao.Google.googleIamDAO
import org.broadinstitute.dsde.workbench.google.model.GoogleProject
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{FreeSpec, Matchers}

class SamApiSpec extends FreeSpec with Matchers with ScalaFutures {
  val anyUser: Credentials = UserPool.chooseAnyUser
  val userAuthToken: AuthToken = anyUser.makeAuthToken()
  implicit override val patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(5, Seconds)))

  def findSaInGoogle(name: WorkbenchUserServiceAccountName): Option[WorkbenchUserServiceAccount] = {
    googleIamDAO.findServiceAccount(GoogleProject(Config.Projects.default), name).futureValue
  }

  def findPetInGoogle(userInfo: UserStatusDetails): Option[WorkbenchUserServiceAccount] = {
    findSaInGoogle(Sam.petName(userInfo))
  }

  "Sam" - {
    "should give pets the same access as their owners" in {

      // set auth tokens explicitly to control which credentials are used

      val userStatus = Sam.user.status()(userAuthToken)

      // ensure known state for pet (not present)

      Sam.removePet(userStatus.userInfo)
      findPetInGoogle(userStatus.userInfo) shouldBe None

      val petAccountEmail = Sam.user.petServiceAccountEmail()(userAuthToken)
      petAccountEmail.value should not be userStatus.userInfo.userEmail
      findPetInGoogle(userStatus.userInfo).map(_.email) shouldBe Some(petAccountEmail)


      // first call should create pet.  confirm that a second call to create/retrieve gives the same results
      Sam.user.petServiceAccountEmail()(userAuthToken) shouldBe petAccountEmail


      val petAuthToken = ServiceAccountAuthToken(petAccountEmail)

      Sam.user.status()(petAuthToken) shouldBe userStatus

      // who is my pet -> who is my user's pet -> it's me
      Sam.user.petServiceAccountEmail()(petAuthToken) shouldBe petAccountEmail

      // clean up

      petAuthToken.removePrivateKey()
      Sam.removePet(userStatus.userInfo)
      findPetInGoogle(userStatus.userInfo) shouldBe None
    }

    def cleanupUser(subjectId: WorkbenchUserServiceAccountSubjectId): Unit = {
      implicit val token: AuthToken = UserPool.chooseAdmin.makeAuthToken()
      if (Sam.admin.doesUserExist(subjectId.value).getOrElse(false)) {
        Sam.admin.deleteUser(subjectId.value)
      }
      Thurloe.keyValuePairs.deleteAll(subjectId.value)
    }

    def registerSaAsNewUser(email: WorkbenchUserServiceAccountEmail)(implicit authToken: AuthToken): Unit = {
      val newUserProfile = Orchestration.profile.BasicProfile (
        firstName = "Generic",
        lastName = "Testerson",
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

    "should not treat non-pet service accounts as pets" in {
      val saEmail = WorkbenchUserServiceAccountEmail(Config.GCS.qaEmail)
      val sa = findSaInGoogle(saEmail.toAccountName).get

      // ensure clean state: SA's user not registered
      cleanupUser(sa.subjectId)

      implicit val saAuthToken: ServiceAccountAuthToken = ServiceAccountAuthToken(saEmail)

      registerSaAsNewUser(saEmail)

      // I am no one's pet.  I am myself.
      Sam.user.status()(saAuthToken).userInfo.userEmail shouldBe saEmail.value

      // clean up
      saAuthToken.removePrivateKey()
      cleanupUser(sa.subjectId)
    }
  }

}