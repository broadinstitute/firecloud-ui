package org.broadinstitute.dsde.firecloud.api

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.auth.AuthToken
import org.broadinstitute.dsde.firecloud.config.Config

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
  }

  object workspaces {

    def create(namespace: String, name: String, authDomain: Set[String] = Set.empty)
              (implicit token: AuthToken): Unit = {
      logger.info(s"Creating workspace: $namespace/$name authDomain: $authDomain")

      val authDomainGroups = authDomain.map(a => Map("membersGroupName" -> a))

      val request = Map("namespace" -> namespace, "name" -> name,
        "attributes" -> Map.empty, "authorizationDomain" -> authDomainGroups)

      postRequest(url + s"api/workspaces", request)
    }

    def delete(namespace: String, name: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Deleting workspace: $namespace/$name")
      deleteRequest(url + s"api/workspaces/$namespace/$name")
    }

    def list()(implicit token: AuthToken):String  = {
      parseResponse(getRequest(url + s"api/workspaces"))
    }
  }
}
