package org.broadinstitute.dsde.firecloud.test.api.sam

import org.broadinstitute.dsde.firecloud.api.Sam
import org.broadinstitute.dsde.firecloud.api.Sam.user.UserStatusDetails
import org.broadinstitute.dsde.firecloud.auth.{AuthToken, ServiceAccountAuthToken}
import org.broadinstitute.dsde.firecloud.config.{Config, Credentials, UserPool}
import org.broadinstitute.dsde.workbench.model.{WorkbenchUserServiceAccount, WorkbenchUserServiceAccountName}
import org.broadinstitute.dsde.firecloud.dao.Google.googleIamDAO
import org.broadinstitute.dsde.workbench.google.model.GoogleProject
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._

class SamApiSpec extends FreeSpec with Matchers {
  val anyUser: Credentials = UserPool.chooseAnyUser
  val userAuthToken: AuthToken = anyUser.makeAuthToken()

  def petName(userInfo: UserStatusDetails) = WorkbenchUserServiceAccountName(s"pet-${userInfo.userSubjectId}")

  def removePet(userInfo: UserStatusDetails): Unit = {
    Sam.admin.deletePetServiceAccount(userInfo.userSubjectId)(UserPool.chooseAdmin.makeAuthToken())
    // TODO: why is this necessary?  GAWB-2867
    val remove = googleIamDAO.removeServiceAccount(GoogleProject(Config.Projects.default), petName(userInfo))
    Await.result(remove, 5.seconds)
  }

  def findPetInGoogle(userInfo: UserStatusDetails): Option[WorkbenchUserServiceAccount] = {
    val find = googleIamDAO.findServiceAccount(GoogleProject(Config.Projects.default), petName(userInfo))
    Await.result(find, 5.seconds)
  }

  "Sam" - {
    "should give pets the same access as their owners" in {

      // set auth tokens explicitly to control which credentials are used

      val userStatus = Sam.user.status()(userAuthToken)

      // ensure known state for pet (not present)

      removePet(userStatus.userInfo)
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
      removePet(userStatus.userInfo)
      findPetInGoogle(userStatus.userInfo) shouldBe None
    }
  }

}