package org.broadinstitute.dsde.firecloud.test.mock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}

trait WireMockFixture extends BeforeAndAfterAll with BeforeAndAfterEach { self: Suite =>

  lazy val wireMockServer: WireMockServer = new WireMockServer(WireMockConfiguration.options().usingFilesUnderDirectory("src/test/scala/org/broadinstitute/dsde/firecloud/wiremock"))

  val wireMockHost: String = "localhost"
  def wireMockPort: Int = 8080

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    wireMockServer.start()
    WireMock.configureFor(wireMockHost, wireMockPort)


  }

  override protected def afterAll(): Unit = {
    wireMockServer.stop()
    super.afterAll()
  }

  override protected def afterEach(): Unit = {
    super.afterEach()
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    WireMock.reset()
  }


}
