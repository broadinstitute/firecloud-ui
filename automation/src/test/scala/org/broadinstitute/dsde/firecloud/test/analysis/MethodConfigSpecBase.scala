package org.broadinstitute.dsde.firecloud.test.analysis

import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.workbench.config.Config
import org.broadinstitute.dsde.workbench.fixture.{MethodData, MethodFixtures, SimpleMethodConfig, WorkspaceFixtures}
import org.broadinstitute.dsde.workbench.service.test.{CleanUp, WebBrowserSpec}
import org.scalatest.{FreeSpec, Matchers}

class MethodConfigSpecBase extends FreeSpec with WebBrowserSpec with CleanUp with WorkspaceFixtures with UserFixtures
  with MethodFixtures with Matchers with LazyLogging {

  val billingProject: String = Config.Projects.default
  val methodName: String = MethodData.SimpleMethod.methodName + "_" + UUID.randomUUID().toString
  val methodConfigName: String = SimpleMethodConfig.configName + "_" + UUID.randomUUID().toString
  val wrongRootEntityErrorText: String = "Error: Method configuration expects an entity of type sample, but you gave us an entity of type participant."
  val noExpressionErrorText: String = "Error: Method configuration expects an entity of type sample, but you gave us an entity of type sample_set."
  val missingInputsErrorText: String = "is missing definitions for these inputs:"


}
