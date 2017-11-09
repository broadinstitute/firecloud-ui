package org.broadinstitute.dsde.firecloud.auth

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory

trait AuthToken {

  val httpTransport = GoogleNetHttpTransport.newTrustedTransport
  val jsonFactory = JacksonFactory.getDefaultInstance
  val authScopes = Seq("profile", "email", "openid", "https://www.googleapis.com/auth/devstorage.full_control", "https://www.googleapis.com/auth/cloud-platform")

  lazy val value: String = makeToken()

  protected def buildCredential(): GoogleCredential

  private def makeToken(): String = {
    val cred = buildCredential()
    cred.refreshToken()
    cred.getAccessToken
  }
}
