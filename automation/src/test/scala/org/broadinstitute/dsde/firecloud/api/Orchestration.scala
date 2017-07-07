package org.broadinstitute.dsde.firecloud.api

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.Config
import org.broadinstitute.dsde.firecloud.auth.AuthToken

import scala.concurrent.duration._


trait Orchestration extends FireCloudClient with LazyLogging {

  def responseAsList(response: String): List[Map[String, Object]] = {
    mapper.readValue(response, classOf[List[Map[String, Object]]])
  }

  private def retry[T](remainingBackOffIntervals: Seq[FiniteDuration])(op: => () => Boolean): Boolean = {
    op() match {
      case true => true
      case false if remainingBackOffIntervals.isEmpty => false
      case false if remainingBackOffIntervals.nonEmpty =>
        Thread sleep remainingBackOffIntervals.head.toMillis
        retry(remainingBackOffIntervals.tail)(op)
    }
  }

  object billing {

    object BillingProjectRole extends Enumeration {
      val User = Value("user")
      val Owner = Value("owner")
    }

    def addUserToBillingProject(projectName: String, email: String, role: BillingProjectRole.Value)(implicit token: AuthToken): Unit = {
      putRequest(Config.FireCloud.orchApiUrl + s"api/billing/$projectName/${role.toString}/$email")
    }

    def createBillingProject(projectName: String, billingAccount: String)(implicit token: AuthToken): Unit = {
      postRequest(Config.FireCloud.orchApiUrl + "api/billing", Map("projectName" -> projectName, "billingAccount" -> billingAccount))

      retry(Seq.fill(30)(10.seconds)) { () =>
        val response: String = getRequest(Config.FireCloud.orchApiUrl + "api/profile/billing")
        val projects: List[Map[String, Object]] = responseAsList(response)
        projects.exists((e) =>
          e.exists(_ == ("creationStatus", "Ready")) && e.exists(_ == ("projectName", projectName)))
      } match {
        case false => throw new Exception("Billing project creation did not complete")
        case true => println("Finished creating billing project")
      }
    }
  }

  object groups {

    object GroupRole extends Enumeration {
      val Member = Value("member")
      val Admin = Value("admin")
    }

    def addUserToGroup(groupName: String, email: String, role: GroupRole.Value)(implicit token: AuthToken): Unit = {
      putRequest(Config.FireCloud.orchApiUrl + s"api/groups/$groupName/${role.toString}/$email")
    }
  }

  object workspaces {

    def create(namespace: String, name: String, authDomain: Option[String] = None)
              (implicit token: AuthToken): Unit = {
      logger.info(s"Creating workspace: $namespace/$name with auth domain: $authDomain")

      val authDomainMap = authDomain map { (a) => Map("authorizationDomain" -> Map("membersGroupName" -> a)) } getOrElse Map()

      val request = Map("namespace" -> namespace,
        "name" -> name, "attributes" -> Map.empty) ++ authDomainMap
      postRequest(Config.FireCloud.orchApiUrl + s"api/workspaces", request)
    }

    def delete(namespace: String, name: String)(implicit token: AuthToken): Unit = {
      logger.info(s"Deleting workspace: $namespace/$name")

      deleteRequest(Config.FireCloud.orchApiUrl + s"api/workspaces/$namespace/$name")
    }

    def updateAcl(namespace: String, name: String, email: String, accessLevel: WorkspaceAccessLevel.Value)(implicit token: AuthToken): Unit = {
      updateAcl(namespace, name, List(AclEntry(email, accessLevel)))
    }

    def updateAcl(namespace: String, name: String, aclEntries: List[AclEntry] = List())(implicit token: AuthToken): Unit = {
      patchRequest(Config.FireCloud.orchApiUrl + s"api/workspaces/$namespace/$name/acl",
        aclEntries.map(e => Map("email" -> e.email, "accessLevel" -> e.accessLevel.toString)))
    }
  }


  /*
   *  Library requests
   */

  def setLibraryAttributes(ns: String, name: String, attributes: Map[String, Any])(implicit token: AuthToken): String = {
    putRequest(Config.FireCloud.orchApiUrl + "api/library/" + ns + "/" + name + "/metadata", attributes)
  }

  def setDiscoverableGroups(ns: String, name: String, groupNames: List[String])(implicit token: AuthToken): String = {
    putRequest(Config.FireCloud.orchApiUrl + "api/library/" + ns + "/" + name + "/discoverableGroups", groupNames)
  }

  def publishWorkspace(ns: String, name: String)(implicit token: AuthToken): String = {
    postRequest(Config.FireCloud.orchApiUrl + "api/library/" + ns + "/" + name + "/published")
  }


  /*
   *  Workspace requests
   */

  def updateAcl(ns: String, name: String, userEmail: String, level: String, canshare: Boolean)(implicit token: AuthToken) = {
    val payload = Seq(Map("email" -> userEmail, "accessLevel" -> level, "canShare" -> canshare))
    patchRequest(Config.FireCloud.orchApiUrl + "api/workspaces/" + ns + "/" + name + "/acl", payload)
  }

  def importMetaData(ns: String, wsName: String, fileName: String, fileContent: String)(implicit token: AuthToken) = {
    postRequestWithMultipart(Config.FireCloud.orchApiUrl + "api/workspaces/" + ns + "/" + wsName + "/" + "importEntities", fileName, fileContent)
  }

}
object Orchestration extends Orchestration

/**
  * Dictionary of access level values expected by the web service API.
  */
object WorkspaceAccessLevel extends Enumeration {
  val NoAccess = Value("NO ACCESS")
  val Owner = Value("OWNER")
  val Reader = Value("READER")
  val Writer = Value("WRITER")
}

case class AclEntry(email: String, accessLevel: WorkspaceAccessLevel.Value)
