package org.broadinstitute.dsde.firecloud.auth

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import org.broadinstitute.dsde.firecloud.config.{Config, Credentials}

import scala.collection.JavaConverters._

// Note: we are creating a new auth token from google every time we call this case class

case class UserAuthToken(user: Credentials) extends AuthToken {
  override protected def buildCredential(): GoogleCredential = {
    val pemfile = new java.io.File(Config.GCS.pathToQAPem)
    val email = Config.GCS.qaEmail

    new GoogleCredential.Builder()
      .setTransport(httpTransport)
      .setJsonFactory(jsonFactory)
      .setServiceAccountId(email)
      .setServiceAccountPrivateKeyFromPemFile(pemfile)
      .setServiceAccountScopes(authScopes.asJava)
      .setServiceAccountUser(user.email)
      .build()
  }
}

