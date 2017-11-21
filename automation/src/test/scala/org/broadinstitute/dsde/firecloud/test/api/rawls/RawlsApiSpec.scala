package org.broadinstitute.dsde.firecloud.test.api.rawls

import java.util.UUID

import com.fasterxml.jackson.annotation.ObjectIdGenerators.UUIDGenerator
import org.broadinstitute.dsde.firecloud.api.{Rawls, Sam}
import org.broadinstitute.dsde.firecloud.api.Sam.user.UserStatusDetails
import org.broadinstitute.dsde.firecloud.auth.{AuthToken, ServiceAccountAuthToken}
import org.broadinstitute.dsde.firecloud.config.{Config, Credentials, UserPool}
import org.broadinstitute.dsde.firecloud.dao.Google.googleIamDAO
import org.broadinstitute.dsde.workbench.google.model.GoogleProject
import org.broadinstitute.dsde.workbench.model.{WorkbenchUserServiceAccount, WorkbenchUserServiceAccountName}
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._

class RawlsApiSpec extends FreeSpec with Matchers {
  val anyUsers: Seq[Credentials] = UserPool.chooseAnyUsers(2)
  val anyUserA: Credentials = anyUsers.head
  val anyUserB:  Credentials = anyUsers.tail.head
  val userAAuthToken: AuthToken = anyUserA.makeAuthToken()
  val userBAuthToken: AuthToken = anyUserB.makeAuthToken()

  val defaultProject:String = Config.Projects.default

  def findPetInGoogle(userInfo: UserStatusDetails): Option[WorkbenchUserServiceAccount] = {

    val find = googleIamDAO.findServiceAccount(GoogleProject(defaultProject), Sam.petName(userInfo))
    Await.result(find, 1.minute)
  }

  "Rawls" - {
    "pets should have same access as their owners" in {

      val uuid = UUID.randomUUID().toString

      val workspaceNameA = "rawls_test_User_A_Workspace" + uuid
      val workspaceNameB = "rawls_test_User_B_Workspace" + uuid

      //Create workspace for User A and User B
      Rawls.workspaces.create(defaultProject, workspaceNameA)(userAAuthToken)
      Rawls.workspaces.create(defaultProject, workspaceNameB)(userBAuthToken)

      //Remove the pet SA for a clean test environment
      val userAStatus = Sam.user.status()(userAAuthToken).get
      Sam.removePet(userAStatus.userInfo)
      findPetInGoogle(userAStatus.userInfo) shouldBe None

      //Validate that the pet SA has been created
      val petAccountEmail = Sam.user.petServiceAccountEmail()(userAAuthToken)
      petAccountEmail.value should not be userAStatus.userInfo.userEmail
      findPetInGoogle(userAStatus.userInfo).map(_.email) shouldBe Some(petAccountEmail)

      val petAuthToken = ServiceAccountAuthToken(petAccountEmail)

      //TODO: Deserialize the json instead of checking for substring
      val petWorkspace = Rawls.workspaces.list()(petAuthToken)
      petWorkspace should include (workspaceNameA)
      petWorkspace should not include (workspaceNameB)

      val userAWorkspace = Rawls.workspaces.list()(userAAuthToken)
      userAWorkspace should include (workspaceNameA)
      userAWorkspace should not include (workspaceNameB)

      val userBWorkspace = Rawls.workspaces.list()(userBAuthToken)
      userBWorkspace should include (workspaceNameB)

      petAuthToken.removePrivateKey()
      Sam.removePet(userAStatus.userInfo)
      findPetInGoogle(userAStatus.userInfo) shouldBe None

      Rawls.workspaces.delete(defaultProject,workspaceNameA)(userAAuthToken)
      Rawls.workspaces.delete(defaultProject,workspaceNameB)(userBAuthToken)
    }
  }
}