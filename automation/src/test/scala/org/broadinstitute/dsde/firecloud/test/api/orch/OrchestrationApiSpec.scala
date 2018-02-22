package org.broadinstitute.dsde.firecloud.test.api.orch

import akka.http.scaladsl.model.StatusCodes
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.services.bigquery.model.{GetQueryResultsResponse, JobReference}
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{Credentials, UserPool}
import org.broadinstitute.dsde.workbench.dao.Google.googleBigQueryDAO
import org.broadinstitute.dsde.workbench.fixture.BillingFixtures
import org.broadinstitute.dsde.workbench.service.{Orchestration, RestException}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Minutes, Second, Seconds, Span}
import org.scalatest.{FreeSpec, Matchers}
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

class OrchestrationApiSpec extends FreeSpec with Matchers with ScalaFutures with Eventually
  with BillingFixtures {
  implicit override val patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(5, Seconds)))

  "Orchestration" - {
    "should grant and remove google role access" in {
      // google roles can take a while to take effect
      implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(10, Minutes)), interval = scaled(Span(10, Seconds)))

      val ownerUser: Credentials = UserPool.chooseProjectOwner
      val ownerToken: AuthToken = ownerUser.makeAuthToken()
      val role = "bigquery.jobUser"

      val user: Credentials = UserPool.chooseStudent
      val userToken: AuthToken = user.makeAuthToken()
      val bigQuery = googleBigQueryDAO(userToken)

      // Willy Shakes uses this insult twice

      val shakespeareQuery = "SELECT COUNT(*) AS scullion_count FROM publicdata.samples.shakespeare WHERE word='scullion'"

      def assertExpectedShakespeareResult(response: GetQueryResultsResponse): Unit = {
        val resultRows = response.getRows
        resultRows.size shouldBe 1
        val resultFields = resultRows.get(0).getF
        resultFields.size shouldBe 1
        resultFields.get(0).getV.toString shouldBe "2"
      }

      withBillingProject("auto-goog-role") { projectName =>
        val preRoleFailure = bigQuery.startQuery(GoogleProject(projectName), "meh").failed.futureValue

        preRoleFailure shouldBe a[GoogleJsonResponseException]
        preRoleFailure.getMessage should include(user.email)
        preRoleFailure.getMessage should include(projectName)
        preRoleFailure.getMessage should include("bigquery.jobs.create")

        Orchestration.billing.addGoogleRoleToBillingProjectUser(projectName, user.email, role)(ownerToken)

        // The google role might not have been applied the first time we call startQuery() - poll until it has
        val queryReference = eventually {
          val start = bigQuery.startQuery(GoogleProject(projectName), shakespeareQuery).futureValue
          start shouldBe a[JobReference]
          start
        }

        // poll for query status until it completes
        val queryJob = eventually {
          val job = bigQuery.getQueryStatus(queryReference).futureValue
          job.getStatus.getState shouldBe "DONE"
          job
        }

        val queryResult = bigQuery.getQueryResult(queryJob).futureValue
        assertExpectedShakespeareResult(queryResult)

        Orchestration.billing.removeGoogleRoleFromBillingProjectUser(projectName, user.email, role)(ownerToken)

        // begin GAWB-3138 why does this fail so often

        // The google role might not have been removed the first time we call startQuery() - poll until it has
        val postRoleFailure = eventually {
          val failure = bigQuery.startQuery(GoogleProject(projectName), shakespeareQuery).failed.futureValue
          failure shouldBe a[GoogleJsonResponseException]
          failure
        }

        postRoleFailure.getMessage should include(user.email)
        postRoleFailure.getMessage should include(projectName)
        postRoleFailure.getMessage should include("bigquery.jobs.create")

        // end GAWB-3138 why does this fail so often

      }(ownerToken)
    }
  }
}