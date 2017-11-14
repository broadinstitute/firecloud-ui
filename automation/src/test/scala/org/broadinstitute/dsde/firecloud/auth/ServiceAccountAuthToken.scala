package org.broadinstitute.dsde.firecloud.auth

import java.io.ByteArrayInputStream

import akka.actor.ActorSystem
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import org.broadinstitute.dsde.firecloud.config.Config
import org.broadinstitute.dsde.workbench.google.HttpGoogleIamDAO
import org.broadinstitute.dsde.workbench.google.model.GoogleProject
import org.broadinstitute.dsde.workbench.model.{WorkbenchUserServiceAccountEmail, WorkbenchUserServiceAccountKey, WorkbenchUserServiceAccountKeyId}

import scala.collection.JavaConverters._
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor}
import scala.concurrent.duration._

// Note: we are creating a new service account private key every time we call this case class

case class ServiceAccountAuthToken(saId: WorkbenchUserServiceAccountEmail) extends AuthToken {

  val appName = "Swintegration"
  val metricBaseName = appName
  lazy val system = ActorSystem()
  val ec: ExecutionContextExecutor = ExecutionContext.global
  lazy val googleIamDAO = new HttpGoogleIamDAO(Config.GCS.qaEmail, Config.GCS.pathToQAPem, appName, metricBaseName)(system, ec)

  // creates a new Google private key.  Be sure to call removePrivateKey() when you are done with it!
  private lazy val serviceAccountPrivateKey: WorkbenchUserServiceAccountKey = {
    Await.result(googleIamDAO.createServiceAccountKey(GoogleProject(Config.Projects.default), saId), 1.minute)
  }

  def removePrivateKey(): Unit = {
    Await.result(googleIamDAO.removeServiceAccountKey(GoogleProject(Config.Projects.default), saId, serviceAccountPrivateKey.id), 1.minute)
  }

  override protected def buildCredential(): GoogleCredential = {
    val privateKeyJsonString = serviceAccountPrivateKey.privateKeyData.decode.get
    GoogleCredential.fromStream(new ByteArrayInputStream(privateKeyJsonString.getBytes())).createScoped(authScopes.asJava)
  }
}
