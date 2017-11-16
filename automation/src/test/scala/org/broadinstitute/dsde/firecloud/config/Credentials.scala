package org.broadinstitute.dsde.firecloud.config

import org.broadinstitute.dsde.firecloud.auth.{AuthToken, UserAuthToken}

case class Credentials (email: String, password: String) {
  def makeAuthToken(): AuthToken = UserAuthToken(this)
}

