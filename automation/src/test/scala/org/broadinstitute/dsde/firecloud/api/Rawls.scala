package org.broadinstitute.dsde.firecloud.api

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.Config
import org.broadinstitute.dsde.firecloud.auth.AuthToken

/**
  * Rawls API service client. This should only be used when Orchestration does
  * not provide a required endpoint. This should primarily be used for admin
  * functions.
  */
object Rawls extends FireCloudClient with LazyLogging {

  private val url = Config.FireCloud.rawlsApiUrl

  object admin {
    def deleteBillingProject(projectName: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Deleting billing project: $projectName")
      deleteRequest(url + s"api/admin/billing/$projectName")
    }

    def deleteUser(subjectId: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Deleting user: $subjectId")
      deleteRequest(url + s"api/admin/user/$subjectId")
    }
  }
}
