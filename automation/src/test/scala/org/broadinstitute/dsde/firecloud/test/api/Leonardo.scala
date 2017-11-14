package org.broadinstitute.dsde.firecloud.test.api

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.api.FireCloudClient
import org.broadinstitute.dsde.firecloud.config.{AuthToken, Config}

/**
  * Leonardo API service client.
  */
object Leonardo extends FireCloudClient with LazyLogging {

  private val url = Config.FireCloud.leonardoApiUrl

  object test {
    def ping()(implicit token: AuthToken): String = {
      logger.info(s"Pinging: GET /ping")
      parseResponse(getRequest(url + "ping"))
    }
  }
}
