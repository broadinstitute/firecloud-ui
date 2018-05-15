package org.broadinstitute.dsde.firecloud.fixture

import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{Credentials, UserPool}
import org.broadinstitute.dsde.workbench.fixture._
import org.broadinstitute.dsde.workbench.service.test.{RandomUtil, WebBrowserSpec}
import org.scalatest.{Matchers, Outcome, fixture}

import scala.util.Try


abstract class BillingFixtureSpec extends fixture.FreeSpec with BillingFixtures with Matchers
  with WebBrowserSpec with UserFixtures with WorkspaceFixtures with GroupFixtures with RandomUtil {

  val owner: Credentials = UserPool.chooseProjectOwner
  implicit val ownerAuthToken: AuthToken = owner.makeAuthToken()
  var claimedBillingProject: ClaimedProject = _

  /**
    * See
    *  https://www.artima.com/docs-scalatest-2.0.M5/org/scalatest/FreeSpec.html
    *   Section: "Overriding withFixture(OneArgTest)"
    *
    * Claim a billing project for project owner
    * @param billingProject
    */
  case class OwnerBillingProjectFixture(billingProject: String)

  type FixtureParam = OwnerBillingProjectFixture

  override def withFixture(test: OneArgTest): Outcome = {
    withFixture(test.toNoArgTest(OwnerBillingProjectFixture(claimedBillingProject.projectName)))
  }

  def claimBillingProject(): Unit = {
    Try {
      claimedBillingProject = claimGPAllocProject(owner)
      logger.info(s"Billing project claimed: ${claimedBillingProject.projectName}")
    }.recover {
      case ex: Exception =>
        logger.error(s"Error occurred in billing project claim as owner $owner", ex)
        throw ex
    }
  }

  def unclaimBillingProject(): Unit = {
    val projectName = claimedBillingProject.projectName
    Try {
      claimedBillingProject.cleanup(owner, List())
      logger.info(s"Billing project unclaimed: $projectName")
    }.recover{
      case ex: Exception =>
        logger.error(s"Error occurred in billing project clean $projectName as owner $owner", ex)
        throw ex
    }
  }

}
