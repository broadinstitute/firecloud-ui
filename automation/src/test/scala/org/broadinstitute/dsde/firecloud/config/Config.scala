package org.broadinstitute.dsde.firecloud.config

object Config extends org.broadinstitute.dsde.automation.config.Config {
  private val fireCloud = config.getConfig("fireCloud")
  private val methodsConfig = config.getConfig("methods")

  object Methods {
    val testMethod = methodsConfig.getString("testMethod")
    val testMethodConfig = methodsConfig.getString("testMethodConfig")
    val methodConfigNamespace = methodsConfig.getString("methodConfigNamespace")
    val snapshotID: Int = methodsConfig.getString("snapshotID").toInt
  }

  object FireCloud {
    val baseUrl: String = fireCloud.getString("baseUrl")
    val fireCloudId: String = fireCloud.getString("fireCloudId")
    val orchApiUrl: String = fireCloud.getString("orchApiUrl")
    val rawlsApiUrl: String = fireCloud.getString("rawlsApiUrl")
    val samApiUrl: String = fireCloud.getString("samApiUrl")
    val thurloeApiUrl: String = fireCloud.getString("thurloeApiUrl")
  }
}
