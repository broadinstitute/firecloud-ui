package org.broadinstitute.dsde.firecloud.config

import com.typesafe.config.ConfigFactory

// questions - can I set application.conf to only have the things to render this?
// or in order to compile do I need to be able to populate the other Config object? (even if i will not run tests with it)

object SmoketestConfig {
  private val config = ConfigFactory.load()
  private val users = config.getConfig("users")
  private val chromeSettings = config.getConfig("chromeSettings")
  private val gcsConfig = config.getConfig("gcs")

  object Users {
    val notSoSecretPassword = users.getString("notSoSecretPassword")
    val testuser = Credentials(users.getString("smoketestuser"), notSoSecretPassword)
  }

  object Projects {
    val default = gcsConfig.getString("serviceProject")
    val common = default
    val billingAccount = gcsConfig.getString("billingAccount")
    val billingAccountId = gcsConfig.getString("billingAccountId")
  }
  object ChromeSettings {
    val chromedriverHost = chromeSettings.getString("chromedriverHost")
    val chromDriverPath = chromeSettings.getString("chromedriverPath")
  }

}
