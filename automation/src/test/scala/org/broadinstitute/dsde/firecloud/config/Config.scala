package org.broadinstitute.dsde.firecloud.config

import com.typesafe.config.ConfigFactory
import scala.util.Random
import scala.util.parsing.json.JSON


/**
  * Set of users mapping name -> Credential
  * Used by UserPool to select a user for a particular function
  */
case class UserSet(userMap: Map[String, Credentials]) {

  def getAllCredentials: Iterable[Credentials] = {
    userMap.values
  }
  def getUserCredential(username: String): Credentials = {
    userMap(username)
  }

  def getRandomCredentials(n: Int): Seq[Credentials] = {
    Random.shuffle(userMap.values.toVector).take(n)
  }
}

object Config {
  private val config = ConfigFactory.load()

  private val fireCloud = config.getConfig("fireCloud")
  private val users = config.getConfig("users")
  private val chromeSettings = config.getConfig("chromeSettings")
  private val gcsConfig = config.getConfig("gcs")
  private val methodsConfig = config.getConfig("methods")


  object GCS {
    val pathToQAPem = gcsConfig.getString("qaPemFile")
    val qaEmail = gcsConfig.getString("qaEmail")
    val appsDomain = gcsConfig.getString("appsDomain")
  }

  object Projects {
    val default = gcsConfig.getString("serviceProject")
    val common = default
    val billingAccount = gcsConfig.getString("billingAccount")
    val billingAccountId = gcsConfig.getString("billingAccountId")
    val smoketestBillingProject = gcsConfig.getString("smoketestsProject")
  }

  object Users {
    private val notSoSecretPassword = users.getString("notSoSecretPassword")
    private val userDataJson = JSON.parseFull(scala.io.Source.fromFile(users.getString("userDataPath")).getLines.mkString).get.asInstanceOf[Map[String, Map[String,String]]]

    def makeCredsMap(jsonMap: Map[String, String]): Map[String, Credentials] = {
      for((k,v) <- jsonMap) yield (k, Credentials(v, notSoSecretPassword))
    }

    val Admins = UserSet(makeCredsMap(userDataJson("admins")))
    val Owners = UserSet(makeCredsMap(userDataJson("owners")))
    val Curators = UserSet(makeCredsMap(userDataJson("curators")))
    val Temps = UserSet(makeCredsMap(userDataJson("temps")))
    val AuthDomainUsers = UserSet(makeCredsMap(userDataJson("authdomains")))
    val Students = UserSet(makeCredsMap(userDataJson("students")))

    // defaults
    val owner = Owners.getUserCredential("hermione")
    val curator = Curators.getUserCredential("mcgonagall")
    val admin = Admins.getUserCredential("dumbledore")
    val testUser = Students.getUserCredential("harry")
    val temp = Temps.getUserCredential("luna")

    val tempSubjectId = users.getString("tempSubjectId")

    val smoketestpassword = users.getString("smoketestpassword")
    val smoketestuser = Credentials(users.getString("smoketestuser"), smoketestpassword)
  }



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
    val leonardoApiUrl: String = fireCloud.getString("leonardoApiUrl")
  }

  object ChromeSettings {
    val chromedriverHost = chromeSettings.getString("chromedriverHost")
    val chromDriverPath = chromeSettings.getString("chromedriverPath")
  }
}
