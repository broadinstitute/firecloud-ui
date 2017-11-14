package org.broadinstitute.dsde.firecloud.test.api.sam

import org.broadinstitute.dsde.firecloud.api.Sam
import org.broadinstitute.dsde.firecloud.api.Sam.user.UserStatusDetails
import org.broadinstitute.dsde.firecloud.auth.{AuthToken, ServiceAccountAuthToken}
import org.broadinstitute.dsde.firecloud.config.{Config, Credentials, UserPool}
import org.broadinstitute.dsde.workbench.model.{WorkbenchUserServiceAccount, WorkbenchUserServiceAccountEmail, WorkbenchUserServiceAccountName}
import org.broadinstitute.dsde.firecloud.dao.Google.googleIamDAO
import org.broadinstitute.dsde.workbench.google.model.GoogleProject
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._

class SamApiSpec extends FreeSpec with Matchers {
  val anyUser: Credentials = UserPool.chooseAnyUser
  val userAuthToken: AuthToken = anyUser.makeAuthToken()

  def findPetInGoogle(userInfo: UserStatusDetails): Option[WorkbenchUserServiceAccount] = {
    val petName = WorkbenchUserServiceAccountName(s"pet-${userInfo.userSubjectId}")
    val find = googleIamDAO.findServiceAccount(GoogleProject(Config.Projects.default), petName)
    Await.result(find, 1.minute)
  }

  "Sam" - {
    "should give pets the same access as their owners" in {

      // set auth tokens explicitly to control which credentials are used

      val userStatus = Sam.user.status()(userAuthToken)

      // ensure known state for pet (not present)

      Sam.admin.deletePetServiceAccount(userStatus.userInfo.userSubjectId)(UserPool.chooseAdmin.makeAuthToken())
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
      Sam.admin.deletePetServiceAccount(userStatus.userInfo.userSubjectId)(UserPool.chooseAdmin.makeAuthToken())
      findPetInGoogle(userStatus.userInfo) shouldBe None
    }
  }
}