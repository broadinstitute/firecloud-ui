package org.broadinstitute.dsde.firecloud.api

import akka.http.scaladsl.model.StatusCodes
import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.automation.api.RestClient
import org.broadinstitute.dsde.automation.config.AuthToken
import org.broadinstitute.dsde.firecloud.config.Config

/**
  * Rawls API service client. This should only be used when Orchestration does
  * not provide a required endpoint. This should primarily be used for admin
  * functions.
  */
object Rawls extends RestClient with LazyLogging {

  private val url = Config.FireCloud.rawlsApiUrl

  object admin {
    def deleteBillingProject(projectName: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Deleting billing project: $projectName")
      deleteRequest(url + s"api/admin/billing/$projectName")
    }
  }
}
