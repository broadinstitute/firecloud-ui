package org.broadinstitute.dsde.firecloud.api

import akka.http.scaladsl.model.StatusCodes
import com.typesafe.scalalogging.LazyLogging
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
