package org.broadinstitute.dsde.firecloud.config

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory

import scala.collection.JavaConverters._


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
  val draco = AuthToken(Config.Users.draco)
}

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


