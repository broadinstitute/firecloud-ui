package org.broadinstitute.dsde.firecloud.config

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory

import scala.collection.JavaConverters._

// todo: maybe make all auth tokens and map them to their credentials?  so when we grab random creds we grab pre-populated auth token?
// is it expensive to make auth tokens?

case class AuthToken(user: Credentials) {

  val httpTransport = GoogleNetHttpTransport.newTrustedTransport
  val jsonFactory = JacksonFactory.getDefaultInstance
  val authScopes = Seq("profile", "email", "openid", "https://www.googleapis.com/auth/devstorage.full_control", "https://www.googleapis.com/auth/cloud-platform")

  lazy val value: String = makeUserToken()

  private def makeUserToken(): String = {
    val cred = buildCredential(user.email)
    cred.refreshToken()
    cred.getAccessToken
  }

  private def buildCredential(userEmail: String): GoogleCredential = {
    val pemfile = new java.io.File(Config.GCS.pathToQAPem)
    val email = Config.GCS.qaEmail

    new GoogleCredential.Builder()
      .setTransport(httpTransport)
      .setJsonFactory(jsonFactory)
      .setServiceAccountId(email)
      .setServiceAccountPrivateKeyFromPemFile(pemfile)
      .setServiceAccountScopes(authScopes.asJava)
      .setServiceAccountUser(userEmail)
      .build()
  }
}


