package org.broadinstitute.dsde.firecloud.auth

import java.io.ByteArrayInputStream

import akka.actor.ActorSystem
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import org.broadinstitute.dsde.firecloud.config.Config
import org.broadinstitute.dsde.workbench.google.HttpGoogleIamDAO
import org.broadinstitute.dsde.workbench.google.model.GoogleProject
import org.broadinstitute.dsde.workbench.model.WorkbenchUserServiceAccountEmail

import scala.collection.JavaConverters._
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor}
import scala.concurrent.duration._

// Note: we are creating a new auth token from google every time we call this case class

case class ServiceAccountAuthToken(saId: WorkbenchUserServiceAccountEmail) extends AuthToken {

  val appName = "Swateroo"
  val metricBaseName = "Swintegration"
  lazy val system = ActorSystem()
  val ec: ExecutionContextExecutor = ExecutionContext.global
  lazy val googleIamDAO = new HttpGoogleIamDAO(Config.GCS.qaEmail, Config.GCS.pathToQAPem, appName, metricBaseName)(system, ec)

  override protected def buildCredential(): GoogleCredential = {
    val newSaKey = googleIamDAO.createServiceAccountKey(GoogleProject(Config.Projects.default), saId)
    val privateKeyJsonString = Await.result(newSaKey, 1.minute).privateKeyData.decode.get

    GoogleCredential.fromStream(new ByteArrayInputStream(privateKeyJsonString.getBytes())).createScoped(authScopes.asJava)
  }
}