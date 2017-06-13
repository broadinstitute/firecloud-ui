package org.broadinstitute.dsde.firecloud

import com.typesafe.config.ConfigFactory
import org.broadinstitute.dsde.firecloud.auth.Credentials

object Config {
  private val config = ConfigFactory.load()
  private val fireCloud = config.getConfig("fireCloud")
  private val accounts = config.getConfig("accounts")
  private val chromeSettings = config.getConfig("chromeSettings")
  private val gcsConfig = config.getConfig("gcs")

  object GCS {
    val pathToQAPem = gcsConfig.getString("qaPemFile")
    val qaEmail = gcsConfig.getString("qaEmail")
  }

  // TODO change to users
  object Accounts {

    val dumbledore = Credentials("dumbledore.admin@quality.firecloud.org", accounts.getString("notSoSecretPassword"))
    val admin = dumbledore
    val hermione = Credentials("hermione.owner@quality.firecloud.org", accounts.getString("notSoSecretPassword"))
    val owner = hermione
    // TODO change these to correct users
    val curator = hermione
    val harry = hermione
    val testUser = harry
    val dominique = harry
    val elvin = harry
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
    val default = "broad-dsde-qa"
  }

  object ChromeSettings {
    val chromedriverHost = chromeSettings.getString("chromedriverHost")

  }
}
