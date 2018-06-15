package org.broadinstitute.dsde.firecloud

import org.broadinstitute.dsde.workbench.config.{CommonConfig, Credentials}

object FireCloudConfig extends CommonConfig {

  object Users extends CommonUsers {
    // defaults
    val owner = Owners.getUserCredential("hermione")
    val temp = Temps.getUserCredential("luna")
    val tempSubjectId = usersConfig.getString("tempSubjectId")

    val smoketestpassword = usersConfig.getString("smoketestpassword")
    val smoketestuser = Credentials(usersConfig.getString("smoketestuser"), smoketestpassword)

    val tcgaJsonWebTokenKey = usersConfig.getString("tcgaJsonWebTokenKey")
  }

  object FireCloud extends CommonFireCloud {
    val baseUrl: String = fireCloudConfig.getString("baseUrl")
    val tcgaAuthDomain: String = fireCloudConfig.getString("tcgaAuthDomain")
  }

  // from common: billingAccountId
  object Projects extends CommonProjects {
    val billingAccount = gcsConfig.getString("billingAccount")
    val smoketestBillingProject = gcsConfig.getString("smoketestsProject")
  }

  object Methods {
    private val methodsConfig = config.getConfig("methods")

    val testMethodConfig = methodsConfig.getString("testMethodConfig")
    val methodConfigNamespace = methodsConfig.getString("methodConfigNamespace")
    val snapshotID: Int = methodsConfig.getString("snapshotID").toInt
  }
}