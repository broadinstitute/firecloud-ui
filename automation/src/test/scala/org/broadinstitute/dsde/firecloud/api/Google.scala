package org.broadinstitute.dsde.firecloud.api

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.auth.AuthToken

/**
  * Created by mbemis on 1/5/18.
  */
object Google extends FireCloudClient with LazyLogging {

  object billing {

    def removeBillingProjectAccount(projectName: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Removing billing account from $projectName")
      putRequest(s"https://content-cloudbilling.googleapis.com/v1/projects/$projectName/billingInfo?fields=billingAccountName", Map("billingAccountName" -> ""))
    }

    def getBillingProjectAccount(projectName: String)(implicit token: AuthToken): Option[String] = {
      logger.info(s"Getting billing account associated with $projectName")
      parseResponseAs[Map[String, String]](getRequest(s"https://content-cloudbilling.googleapis.com/v1/projects/$projectName/billingInfo?fields=billingAccountName")).get("billingAccountName")
    }
  }
}
