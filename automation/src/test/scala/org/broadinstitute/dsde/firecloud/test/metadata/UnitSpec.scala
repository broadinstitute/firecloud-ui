package org.broadinstitute.dsde.firecloud.test.metadata

import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.UserPool
import org.scalatest.{BeforeAndAfterAll, Outcome, fixture}
import org.broadinstitute.dsde.workbench.fixture._


abstract class UnitSpec extends fixture.FreeSpec with BeforeAndAfterAll with BillingFixtures {

  val owner = UserPool.chooseProjectOwner
  implicit val ownerAuthToken: AuthToken = owner.makeAuthToken()
  var claimedBillingProject: ClaimedProject = _

  /**
    * See
    *  https://www.artima.com/docs-scalatest-2.0.M5/org/scalatest/FreeSpec.html
    *   Section: "Overriding withFixture(OneArgTest)"
    *
    * @param billingProject
    */
  case class billingFixture(billingProject: String)

  type FixtureParam = billingFixture

  override def withFixture(test: OneArgTest): Outcome = {
    withFixture(test.toNoArgTest(billingFixture(claimedBillingProject.projectName)))
  }

  override def beforeAll(): Unit = {
    claimedBillingProject = claimGPAllocProject(owner)
    logger.info(s"beforeAll(): billing project created - ${claimedBillingProject.projectName}" )
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    try {
      super.afterAll()
    } finally {
      val name = claimedBillingProject.projectName
      claimedBillingProject.cleanup(owner, List())
      logger.info(s"afterAll: billing project cleaned - ${name}" )
    }
  }
}
