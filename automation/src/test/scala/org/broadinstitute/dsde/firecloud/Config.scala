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

  object Projects {
    val default = gcsConfig.getString("serviceProject")
    val common = default
    val billingAccount = gcsConfig.getString("billingAccount")
  }

  object Users {
    val notSoSecretPassword = accounts.getString("notSoSecretPassword")

    val dumbledore = Credentials(accounts.getString("dumbledore"), notSoSecretPassword)
    val voldemort = Credentials(accounts.getString("voldemort"), notSoSecretPassword)
    val admin = dumbledore

    val hermione = Credentials(accounts.getString("hermione"), notSoSecretPassword)
    val owner = hermione

    val mcgonagall = Credentials(accounts.getString("mcgonagall"), notSoSecretPassword)
    val snape = Credentials(accounts.getString("snape"), notSoSecretPassword)
    val curator = mcgonagall

    val harry = Credentials(accounts.getString("harry"), notSoSecretPassword)
    val ron = Credentials(accounts.getString("ron"), notSoSecretPassword)
    val draco = Credentials(accounts.getString("draco"), notSoSecretPassword)

    val fred = Credentials(accounts.getString("fred"), notSoSecretPassword)
    val george = Credentials(accounts.getString("george"), notSoSecretPassword)
    val bill = Credentials(accounts.getString("bill"), notSoSecretPassword)

    val lunaTemp = Credentials(accounts.getString("luna"), notSoSecretPassword)
    val nevilleTemp = Credentials(accounts.getString("neville"), notSoSecretPassword)
    val testUser = harry
    val dominique = harry
    val elvin = fred
  }

  object FireCloud {
    val baseUrl = fireCloud.getString("baseUrl")
    val local_API= "http://localhost:8080/"
    val apiUrl = fireCloud.getString("apiUrl")
    val rawlsApiUrl = fireCloud.getString("rawlsApiUrl")
    val THURLOE_API=fireCloud.getString("thurloeApiUrl")
  }

  object ChromeSettings {
    val chromedriverHost = chromeSettings.getString("chromedriverHost")

  }
}
