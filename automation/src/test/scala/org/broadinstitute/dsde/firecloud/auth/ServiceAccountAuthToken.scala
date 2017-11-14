package org.broadinstitute.dsde.firecloud.auth

import java.io.ByteArrayInputStream

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import org.broadinstitute.dsde.firecloud.config.Config
import org.broadinstitute.dsde.firecloud.dao.Google.googleIamDAO
import org.broadinstitute.dsde.workbench.google.model.GoogleProject
import org.broadinstitute.dsde.workbench.model.{WorkbenchUserServiceAccountEmail, WorkbenchUserServiceAccountKey, WorkbenchUserServiceAccountKeyId}

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration._

// Note: we are creating a new service account private key every time we call this case class

case class ServiceAccountAuthToken(saId: WorkbenchUserServiceAccountEmail) extends AuthToken {

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
