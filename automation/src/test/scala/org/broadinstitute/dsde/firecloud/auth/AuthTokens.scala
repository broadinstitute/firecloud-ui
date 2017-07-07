package org.broadinstitute.dsde.firecloud.auth

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import org.broadinstitute.dsde.firecloud.Config
import scala.collection.JavaConversions._


object AuthTokens {
  val dumbledore = AuthToken(Config.Users.dumbledore)
  val admin = dumbledore
  val hermione = AuthToken(Config.Users.hermione)
  val owner = hermione
  val mcgonagall = AuthToken(Config.Users.mcgonagall)
  val curator = mcgonagall
  val harry = AuthToken(Config.Users.harry)
  val testUser = harry
  val dominique = harry
  val fred = AuthToken(Config.Users.fred)
  val elvin = fred
  val george = AuthToken(Config.Users.george)
  val bill = AuthToken(Config.Users.bill)
  val lunaTemp = AuthToken(Config.Users.lunaTemp)
  val nevilleTemp = AuthToken(Config.Users.nevilleTemp)
}

case class AuthToken(value: String)

case object AuthToken {
  def apply(user: Credentials): AuthToken = getUserToken(user.email)

  val httpTransport = GoogleNetHttpTransport.newTrustedTransport
  val jsonFactory = JacksonFactory.getDefaultInstance
  val authScopes = Seq("profile", "email", "openid", "https://www.googleapis.com/auth/devstorage.full_control", "https://www.googleapis.com/auth/cloud-platform")

  def getUserToken(userEmail: String): AuthToken = {
    val cred = buildCredential(userEmail)
    cred.refreshToken()
    AuthToken(cred.getAccessToken)
  }

  def buildCredential(userEmail: String): GoogleCredential = {
    val pemfile = new java.io.File(Config.GCS.pathToQAPem)
    val email = Config.GCS.qaEmail

    new GoogleCredential.Builder()
      .setTransport(httpTransport)
      .setJsonFactory(jsonFactory)
      .setServiceAccountId(email)
      .setServiceAccountPrivateKeyFromPemFile(pemfile)
      .setServiceAccountScopes(authScopes)
      .setServiceAccountUser(userEmail)
      .build();
  }
}


