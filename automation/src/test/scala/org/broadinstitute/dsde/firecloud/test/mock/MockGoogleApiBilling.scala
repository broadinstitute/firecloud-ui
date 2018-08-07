package org.broadinstitute.dsde.firecloud.test.mock

import java.util.concurrent.TimeUnit

import com.github.tomakehurst.wiremock.client.WireMock
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{FreeSpec, Matchers}
import dispatch.{Http, url}

import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor}
import scala.concurrent.duration.Duration

class MockGoogleApiBilling extends FreeSpec with WireMockFixture with LazyLogging with Matchers {

  "WireMock should stub get googleapi billing request" in {
    val urlPath = "/v1/billingAccounts"
    wireMockServer.stubFor(WireMock.get(urlPath)
      .willReturn(WireMock.aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json; charset=UTF-8")
        .withBodyFile("body-googleapi-v1-billingAccounts-list-get.json")))

    implicit val ec: ExecutionContextExecutor = ExecutionContext.global
    val request = url(s"http://$wireMockHost:$wireMockPort$urlPath").GET
    val responseFuture = Http.default(request)

    val response = Await.result(responseFuture, Duration(2000, TimeUnit.MILLISECONDS))
    response.getStatusCode should be(200)

  }

}
