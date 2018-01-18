package org.broadinstitute.dsde.firecloud.test.api.sam

import org.broadinstitute.dsde.firecloud.api.{Orchestration, Sam}
import org.broadinstitute.dsde.firecloud.api.Sam.user.UserStatusDetails
import org.broadinstitute.dsde.firecloud.auth.{AuthToken, ServiceAccountAuthToken}
import org.broadinstitute.dsde.firecloud.config.{Config, Credentials, UserPool}
import org.broadinstitute.dsde.workbench.model._
import org.broadinstitute.dsde.firecloud.dao.Google.googleIamDAO
import org.broadinstitute.dsde.firecloud.test.CleanUp
import org.broadinstitute.dsde.workbench.model.google.{GoogleProject, ServiceAccount, ServiceAccountName}
import org.broadinstitute.dsde.workbench.service.Thurloe
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{FreeSpec, Matchers}

class SamApiSpec extends FreeSpec with Matchers with ScalaFutures with CleanUp {
  implicit override val patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(5, Seconds)))

  val thurloe = new Thurloe(Config.FireCloud.thurloeApiUrl, Config.FireCloud.fireCloudId)

  def findSaInGoogle(name: ServiceAccountName): Option[ServiceAccount] = {
    googleIamDAO.findServiceAccount(GoogleProject(Config.Projects.default), name).futureValue
  }

  def findPetInGoogle(userInfo: UserStatusDetails): Option[ServiceAccount] = {
    findSaInGoogle(Sam.petName(userInfo))
  }

  def registerAsNewUser(email: WorkbenchEmail)(implicit authToken: AuthToken): Unit = {
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

  def removeUser(subjectId: String): Unit = {
    implicit val token: AuthToken = UserPool.chooseAdmin.makeAuthToken()
    if (Sam.admin.doesUserExist(subjectId).getOrElse(false)) {
      Sam.admin.deleteUser(subjectId)
    }
    thurloe.keyValuePairs.deleteAll(subjectId)
  }

  "Sam test utilities" - {
    "should be idempotent for user registration and removal" in {

      // use a temp user because they should not be registered.  Remove them after!

      val tempUser: Credentials = UserPool.chooseTemp
      val tempAuthToken: AuthToken = tempUser.makeAuthToken()

      Sam.user.status()(tempAuthToken) shouldBe None

      registerAsNewUser(WorkbenchEmail(tempUser.email))(tempAuthToken)

      val tempUserInfo = Sam.user.status()(tempAuthToken).get.userInfo
      tempUserInfo.userEmail shouldBe tempUser.email

      // OK to re-register

      registerAsNewUser(WorkbenchEmail(tempUser.email))(tempAuthToken)
      Sam.user.status()(tempAuthToken).get.userInfo.userEmail shouldBe tempUser.email

      removeUser(tempUserInfo.userSubjectId)
      Sam.user.status()(tempAuthToken) shouldBe None

      // OK to re-remove

      removeUser(tempUserInfo.userSubjectId)
      Sam.user.status()(tempAuthToken) shouldBe None
    }
  }

  "Sam" - {
    "should give pets the same access as their owners" in {
      val anyUser: Credentials = UserPool.chooseAnyUser
      val userAuthToken: AuthToken = anyUser.makeAuthToken()

      // set auth tokens explicitly to control which credentials are used

      val userStatus = Sam.user.status()(userAuthToken).get

      // ensure known state for pet (not present)

      Sam.removePet(userStatus.userInfo)
      findPetInGoogle(userStatus.userInfo) shouldBe None

      val petAccountEmail = Sam.user.petServiceAccountEmail()(userAuthToken)
      petAccountEmail.value should not be userStatus.userInfo.userEmail
      findPetInGoogle(userStatus.userInfo).map(_.email) shouldBe Some(petAccountEmail)


      // first call should create pet.  confirm that a second call to create/retrieve gives the same results
      Sam.user.petServiceAccountEmail()(userAuthToken) shouldBe petAccountEmail


      val petAuthToken = ServiceAccountAuthToken(petAccountEmail)
      register cleanUp petAuthToken.removePrivateKey()

      Sam.user.status()(petAuthToken) shouldBe Some(userStatus)

      // who is my pet -> who is my user's pet -> it's me
      Sam.user.petServiceAccountEmail()(petAuthToken) shouldBe petAccountEmail

      // clean up

      Sam.removePet(userStatus.userInfo)
      findPetInGoogle(userStatus.userInfo) shouldBe None
    }

    "should not treat non-pet service accounts as pets" in {
      val saEmail = WorkbenchEmail(Config.GCS.qaEmail)
      val sa = findSaInGoogle(google.toAccountName(saEmail)).get

      // ensure clean state: SA's user not registered
      removeUser(sa.subjectId.value)

      implicit val saAuthToken: ServiceAccountAuthToken = ServiceAccountAuthToken(saEmail)
      register cleanUp saAuthToken.removePrivateKey()

      registerAsNewUser(saEmail)

      // I am no one's pet.  I am myself.
      Sam.user.status()(saAuthToken).map(_.userInfo.userEmail) shouldBe Some(saEmail.value)

      // clean up

      removeUser(sa.subjectId.value)
    }

    "should retrieve a user's proxy group as any user" in {
      val Seq(user1: Credentials, user2: Credentials) = UserPool.chooseStudents(2)
      val authToken1: AuthToken = user1.makeAuthToken()
      val authToken2: AuthToken = user2.makeAuthToken()

      val info1 = Sam.user.status()(authToken1).get.userInfo
      val info2 = Sam.user.status()(authToken2).get.userInfo
      val email1 = WorkbenchEmail(Sam.user.status()(authToken1).get.userInfo.userEmail)
      val email2 = WorkbenchEmail(Sam.user.status()(authToken2).get.userInfo.userEmail)
      val userId1 = Sam.user.status()(authToken1).get.userInfo.userSubjectId
      val userId2 = Sam.user.status()(authToken2).get.userInfo.userSubjectId

      val proxyGroup1_1 = Sam.user.proxyGroup(email1)(authToken1)
      val proxyGroup1_2 = Sam.user.proxyGroup(email1)(authToken2)
      val proxyGroup2_1 = Sam.user.proxyGroup(email2)(authToken1)
      val proxyGroup2_2 = Sam.user.proxyGroup(email2)(authToken2)

      // will break when Sam's implementation changes
      proxyGroup1_1 shouldBe WorkbenchEmail(s"PROXY_$userId1@${Config.GCS.appsDomain}")
      proxyGroup1_2 shouldBe WorkbenchEmail(s"PROXY_$userId1@${Config.GCS.appsDomain}")
      proxyGroup2_1 shouldBe WorkbenchEmail(s"PROXY_$userId2@${Config.GCS.appsDomain}")
      proxyGroup2_2 shouldBe WorkbenchEmail(s"PROXY_$userId2@${Config.GCS.appsDomain}")
    }
  }

}