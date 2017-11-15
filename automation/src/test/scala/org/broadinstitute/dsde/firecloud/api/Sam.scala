package org.broadinstitute.dsde.firecloud.api

import akka.http.scaladsl.model.StatusCodes
import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.api.Sam.user.UserStatusDetails
import org.broadinstitute.dsde.firecloud.config.UserPool
import org.broadinstitute.dsde.firecloud.dao.Google.googleIamDAO
import org.broadinstitute.dsde.workbench.google.model.GoogleProject
import org.broadinstitute.dsde.workbench.model.WorkbenchUserServiceAccountName
import scala.concurrent.duration._
import scala.concurrent.Await
import org.broadinstitute.dsde.firecloud.auth.AuthToken
import org.broadinstitute.dsde.firecloud.config.Config
import org.broadinstitute.dsde.workbench.model.WorkbenchUserServiceAccountEmail

/**
  * Sam API service client. This should only be used when Orchestration does
  * not provide a required endpoint. This should primarily be used for admin
  * functions.
  */
object Sam extends FireCloudClient with LazyLogging {

  private val url = Config.FireCloud.samApiUrl

  object admin {

    def deleteUser(subjectId: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Deleting user: $subjectId")
      deleteRequest(url + s"api/admin/user/$subjectId")
    }

    def doesUserExist(subjectId: String)(implicit token: AuthToken): Option[Boolean] = {
      getRequest(url + s"api/admin/user/$subjectId").status match {
        case StatusCodes.OK => Option(true)
        case StatusCodes.NotFound => Option(false)
        case _ => None
      }
    }

    def deletePetServiceAccount(userSubjectId: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Deleting pet service account for user $userSubjectId")
      deleteRequest(url + s"api/admin/user/$userSubjectId/petServiceAccount")
    }

    def petName(userInfo: UserStatusDetails) = WorkbenchUserServiceAccountName(s"pet-${userInfo.userSubjectId}")

    def removePet(userInfo: UserStatusDetails): Unit = {
      Sam.admin.deletePetServiceAccount(userInfo.userSubjectId)(UserPool.chooseAdmin.makeAuthToken())
      val remove = googleIamDAO.removeServiceAccount(GoogleProject(Config.Projects.default), petName(userInfo))
      Await.result(remove, 5.seconds)
    }
  }

  object user {
    case class UserStatusDetails(userSubjectId: String, userEmail: String)
    case class UserStatus(userInfo: UserStatusDetails, enabled: Map[String, Boolean])

    def status()(implicit token: AuthToken): UserStatus = {
      logger.info(s"Getting user registration status")
      parseResponseAs[UserStatus](getRequest(url + "register/user"))
    }

    def petServiceAccountEmail()(implicit token: AuthToken): WorkbenchUserServiceAccountEmail = {
      logger.info(s"Getting pet service account email")
      val petEmailStr = parseResponseAs[String](getRequest(url + "api/user/petServiceAccount"))
      WorkbenchUserServiceAccountEmail(petEmailStr)
    }
  }
}
