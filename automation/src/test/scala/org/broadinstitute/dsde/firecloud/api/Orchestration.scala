package org.broadinstitute.dsde.firecloud.api

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.api.WorkspaceAccessLevel.WorkspaceAccessLevel
import org.broadinstitute.dsde.firecloud.config.{AuthToken, Config}
import org.broadinstitute.dsde.firecloud.util.Retry.retry

import scala.concurrent.duration._


trait Orchestration extends FireCloudClient with LazyLogging {

  def responseAsList(response: String): List[Map[String, Object]] = {
    mapper.readValue(response, classOf[List[Map[String, Object]]])
  }

  private def apiUrl(s: String) = {
    Config.FireCloud.orchApiUrl + s
  }

  object billing {

    object BillingProjectRole extends Enumeration {
      type BillingProjectRole = Value
      val User = Value("user")
      val Owner = Value("owner")
    }
    import BillingProjectRole._

    def addUserToBillingProject(projectName: String, email: String, role: BillingProjectRole)(implicit token: AuthToken): Unit = {
      logger.info(s"Adding user to billing project: $projectName $email ${role.toString}")
      putRequest(apiUrl(s"api/billing/$projectName/${role.toString}/$email"))
    }

    def createBillingProject(projectName: String, billingAccount: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Creating billing project: $projectName $billingAccount")
      postRequest(apiUrl("api/billing"), Map("projectName" -> projectName, "billingAccount" -> billingAccount))

      retry(10.seconds, 5.minutes)({
              val response: String = parseResponse(getRequest(apiUrl("api/profile/billing")))
              val projects: List[Map[String, Object]] = responseAsList(response)
              projects.find((e) =>
                e.exists(_ == ("creationStatus", "Ready")) && e.exists(_ == ("projectName", projectName)))
            }) match {
        case None => throw new Exception("Billing project creation did not complete")
        case Some(_) => logger.info(s"Finished creating billing project: $projectName $billingAccount")
      }
    }
  }

  object groups {

    object GroupRole extends Enumeration {
      type GroupRole = Value
      val Member = Value("member")
      val Admin = Value("admin")
    }
    import GroupRole._

    def addUserToGroup(groupName: String, email: String, role: GroupRole)(implicit token: AuthToken): Unit = {
      logger.info(s"Adding user to group: $groupName $email ${role.toString}")
      putRequest(apiUrl(s"api/groups/$groupName/${role.toString}/$email"))
    }

    def create(groupName: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Creating group: $groupName")
      postRequest(apiUrl(s"api/groups/$groupName"))
    }

    def delete(groupName: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Deleting group: $groupName")
      deleteRequest(apiUrl(s"api/groups/$groupName"))
    }

    def removeUserFromGroup(groupName: String, email: String, role: GroupRole)(implicit token: AuthToken): Unit = {
      logger.info(s"Removing user from group: $groupName $email ${role.toString}")
      deleteRequest(apiUrl(s"api/groups/$groupName/${role.toString}/$email"))
    }
  }

  object workspaces {

    def create(namespace: String, name: String, authDomain: String = "")
              (implicit token: AuthToken): Unit = {
      logger.info(s"Creating workspace: $namespace/$name authDomain: $authDomain")

      val authDomainMap = authDomain match {
        case "" => Map()
        case a => Map("authorizationDomain" -> Map("membersGroupName" -> a))
      }

      val request = Map("namespace" -> namespace,
        "name" -> name, "attributes" -> Map.empty) ++ authDomainMap
      postRequest(apiUrl(s"api/workspaces"), request)
    }

    def delete(namespace: String, name: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Deleting workspace: $namespace/$name")
      deleteRequest(apiUrl(s"api/workspaces/$namespace/$name"))
    }

    def updateAcl(namespace: String, name: String, email: String, accessLevel: WorkspaceAccessLevel)(implicit token: AuthToken): Unit = {
      updateAcl(namespace, name, List(AclEntry(email, accessLevel)))
    }

    def updateAcl(namespace: String, name: String, aclEntries: List[AclEntry] = List())(implicit token: AuthToken): Unit = {
      logger.info(s"Updating ACLs for workspace: $namespace/$name $aclEntries")
      patchRequest(apiUrl(s"api/workspaces/$namespace/$name/acl"),
        aclEntries.map(e => Map("email" -> e.email, "accessLevel" -> e.accessLevel.toString)))
    }
  }


  /*
   *  Library requests
   */

  object library {
    def setLibraryAttributes(ns: String, name: String, attributes: Map[String, Any])(implicit token: AuthToken): String = {
      logger.info(s"Setting library attributes for workspace: $ns/$name $attributes")
      putRequest(apiUrl(s"api/library/$ns/$name/metadata"), attributes)
    }

    def setDiscoverableGroups(ns: String, name: String, groupNames: List[String])(implicit token: AuthToken): String = {
      logger.info(s"Setting discoverable groups for workspace: $ns/$name $groupNames")
      putRequest(apiUrl(s"api/library/$ns/$name/discoverableGroups"), groupNames)
    }

    def publishWorkspace(ns: String, name: String)(implicit token: AuthToken): String = {
      logger.info(s"Publishing workspace: $ns/$name")
      postRequest(apiUrl(s"api/library/$ns/$name/published"))
    }

    def unpublishWorkspace(ns: String, name: String)(implicit token: AuthToken): String = {
      logger.info(s"Unpublishing workspace: $ns/$name")
      deleteRequest(apiUrl(s"api/library/$ns/$name/published"))
    }
  }

  /*
   *  Method Configurations requests
   */

  object methodConfigurations {

    //    This only works for method configs, but not methods
    def copyMethodConfigFromMethodRepo(ns: String, wsName: String, configurationNamespace: String, configurationName: String, configurationSnapshotId: Int, destinationNamespace: String, destinationName: String)(implicit token: AuthToken): String = {
      logger.info(s"Copying method config from method repo: $ns/$wsName config: $configurationNamespace/$configurationName $configurationSnapshotId destination: $destinationNamespace/$destinationName")
      postRequest(apiUrl(s"api/workspaces/$ns/$wsName/method_configs/copyFromMethodRepo"),
        Map("configurationNamespace" -> configurationNamespace, "configurationName" -> configurationName, "configurationSnapshotId" -> configurationSnapshotId, "destinationNamespace" -> destinationNamespace, "destinationName" -> destinationName))
    }

    def createMethodConfigInWorkspace(ns: String, wsName: String, methodConfigVersion: Int,
                                      methodNamespace: String, methodName: String, methodVersion: Int,
                                      destinationNamespace: String, destinationName: String,
                                      inputName: String, inputText: String, outputName: String, outputText: String,
                                      rootEntityType: String)(implicit token: AuthToken): String = {
      logger.info(s"Creating method config: $ns/$wsName $methodConfigVersion method: $methodNamespace/$methodName destination: $destinationNamespace/$destinationName")
      postRequest(apiUrl(s"api/workspaces/$ns/$wsName/methodconfigs"),
        Map("deleted" -> false,
          "inputs" -> Map(inputName -> inputText),
          "methodConfigVersion" -> methodConfigVersion,
          "methodRepoMethod" -> Map("methodNamespace" -> methodNamespace, "methodName" -> methodName, "methodVersion" -> methodVersion),
          "namespace" -> destinationNamespace, "name" -> destinationName,
          "outputs" -> Map(outputName -> outputText),
          "prerequisites" -> Map(),
          "rootEntityType" -> rootEntityType)
      )
    }

  }

  /*
   *  Submissions requests
   */

  object submissions {
    def launchWorkflow(ns: String, wsName: String, methodConfigurationNamespace: String, methodConfigurationName: String, entityType: String, entityName: String, expression: String, useCallCache: Boolean, workflowFailureMode: String = "NoNewCalls")(implicit token: AuthToken): String = {
      logger.info(s"Creating a submission: $ns/$wsName config: $methodConfigurationNamespace/$methodConfigurationName")
      postRequest(apiUrl(s"api/workspaces/$ns/$wsName/submissions"),
        Map("methodConfigurationNamespace" -> methodConfigurationNamespace, "methodConfigurationName" -> methodConfigurationName, "entityType" -> entityType, "entityName" -> entityName, "expression" -> expression, "useCallCache" -> useCallCache, "workflowFailureMode" -> workflowFailureMode))
    }

  }


  /*
   *  Workspace requests
   */

  def importMetaData(ns: String, wsName: String, fileName: String, fileContent: String)(implicit token: AuthToken): String = {
    logger.info(s"Importing metadata: $ns/$wsName $fileName")
    postRequestWithMultipart(apiUrl(s"api/workspaces/$ns/$wsName/importEntities"), fileName, fileContent)
  }

}
object Orchestration extends Orchestration

/**
  * Dictionary of access level values expected by the web service API.
  */
object WorkspaceAccessLevel extends Enumeration {
  type WorkspaceAccessLevel = Value
  val NoAccess = Value("NO ACCESS")
  val Owner = Value("OWNER")
  val Reader = Value("READER")
  val Writer = Value("WRITER")
}

case class AclEntry(email: String, accessLevel: WorkspaceAccessLevel)
