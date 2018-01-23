package org.broadinstitute.dsde.firecloud.test.library

import org.broadinstitute.dsde.firecloud.fixture.{UserFixtures, WorkspaceFixtures}
import org.broadinstitute.dsde.firecloud.test.{CleanUp, WebBrowserSpec}
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class DataUseAwareSearchSpec extends FreeSpec with WebBrowserSpec with UserFixtures with WorkspaceFixtures with CleanUp with Matchers {
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global

}
