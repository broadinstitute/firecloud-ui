package org.broadinstitute.dsde.firecloud

import com.typesafe.config.ConfigFactory
import org.broadinstitute.dsde.firecloud.api.AuthToken

object Config {
  private val config = ConfigFactory.load()
  private val fireCloud = config.getConfig("fireCloud")
  private val accounts = config.getConfig("accounts")
  private val authTokens = config.getConfig("authTokens")

  object EnvironmentVars {
    val GCLOUD_AUTH_TOKEN = "ya29.Gl1WBGPL_eVgVOXoniFCFSQh-FFtIao5kJfUIfJ5KrIaleWzKxwbWdTy0ZondNtoJp0CDKNG7ODWYy8CU3dMRnIh9tKy6dLdopDNDogJVfHVIB5lcbARotan7dPbOmo"
  }

  case class Credentials(email: String, password: String)

  object Accounts {
//    private val accounts = config.getConfig("accounts")
    val curatorUserEmail = "test.firec@gmail.com"
    val curatorUserPassword = accounts.getString("notSoSecretPassword")
    val testUserEmail = "test.firec@gmail.com"
    val testUserPassword = accounts.getString("notSoSecretPassword")
    val adminUserEmail = "b.adm.firec@gmail.com"
    val adminUserPassword = accounts.getString("notSoSecretPassword")

    val testFireC = Credentials("test.firec@gmail.com", accounts.getString("notSoSecretPassword"))
    val dominique = Credentials("dominique.testerson@gmail.com", accounts.getString("notSoSecretPassword"))
    val elvin = Credentials("elvin.testerson@gmail.com", accounts.getString("notSoSecretPassword"))
  }

  object AuthTokens {
    val admin = AuthToken(authTokens.getString("admin"))
    val elvin = AuthToken(authTokens.getString("elvin"))
    val testFireC = AuthToken(authTokens.getString("testFireC"))
  }

  object FireCloud {
    val baseUrl = fireCloud.getString("baseUrl")
    val local_API= "http://localhost:8080/"
    val apiUrl = fireCloud.getString("apiUrl")
    val rawlsApiUrl = fireCloud.getString("rawlsApiUrl")
    val THURLOE_API="https://firecloud-fiab.dsde-dev.broadinstitute.org:25443/"
  }

  object Projects {
    val common = "security-spec-test5"
  }
}
