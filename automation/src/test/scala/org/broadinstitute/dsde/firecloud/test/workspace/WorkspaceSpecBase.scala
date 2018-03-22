package org.broadinstitute.dsde.firecloud.test.workspace

import java.util.UUID

import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{Config, Credentials, UserPool}
import org.broadinstitute.dsde.workbench.fixture.{MethodFixtures, SimpleMethodConfig, WorkspaceFixtures}
import org.broadinstitute.dsde.workbench.service.test.{CleanUp, WebBrowserSpec}
import org.scalatest.{FreeSpec, Matchers}

abstract class WorkspaceSpecBase extends FreeSpec with WebBrowserSpec with WorkspaceFixtures with UserFixtures
  with MethodFixtures with CleanUp with Matchers {

  val projectOwner: Credentials = UserPool.chooseProjectOwner
  val authTokenOwner: AuthToken = projectOwner.makeAuthToken()
  val methodConfigName: String = SimpleMethodConfig.configName + "_" + UUID.randomUUID().toString + "Config"
  val billingProject: String = Config.Projects.default


}
