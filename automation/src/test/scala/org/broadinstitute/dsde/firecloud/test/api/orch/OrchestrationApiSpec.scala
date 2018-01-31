package org.broadinstitute.dsde.firecloud.test.api.orch

import akka.http.scaladsl.model.StatusCodes
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.services.bigquery.model.GetQueryResultsResponse
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{Credentials, UserPool}
import org.broadinstitute.dsde.workbench.dao.Google
import org.broadinstitute.dsde.workbench.fixture.BillingFixtures
import org.broadinstitute.dsde.workbench.google.{GoogleBigQueryDAO, GoogleCredentialModes, HttpGoogleBigQueryDAO}
import org.broadinstitute.dsde.workbench.service.{Orchestration, RestException}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{FreeSpec, Matchers}
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.broadinstitute.dsde.workbench.service.util.Retry

import scala.concurrent.duration._
import scala.util.Try

class OrchestrationApiSpec extends FreeSpec with Matchers with ScalaFutures with Eventually
  with BillingFixtures {
  implicit override val patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(5, Seconds)))

  def bqDao(userToken: String): GoogleBigQueryDAO = {
    new HttpGoogleBigQueryDAO(Google.appName, GoogleCredentialModes.Token(userToken), Google.metricBaseName)(Google.system, Google.ec)
  }

  "Orchestration" - {
    "should grant and remove google role access" in {
      val ownerUser: Credentials = UserPool.chooseProjectOwner
      val ownerToken: AuthToken = ownerUser.makeAuthToken()
      val role = "bigquery.jobUser"

      val user: Credentials = UserPool.chooseStudent
      val userToken: AuthToken = user.makeAuthToken()

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
        val preRoleFailure = bqDao(userToken.value).startQuery(GoogleProject(projectName), "meh").failed.futureValue
        preRoleFailure shouldBe a[GoogleJsonResponseException]
        preRoleFailure.getMessage should include(user.email)
        preRoleFailure.getMessage should include(projectName)
        preRoleFailure.getMessage should include("bigquery.jobs.create")

        Orchestration.billing.addGoogleRoleToBillingProjectUser(projectName, user.email, role)(ownerToken)

        val queryReference = bqDao(userToken.value).startQuery(GoogleProject(projectName), shakespeareQuery).futureValue
        val queryJob = eventually {
          val job = bqDao(userToken.value).getQueryStatus(queryReference).futureValue
          job.getStatus.getState shouldBe "DONE"
          job
        }

        val queryResult = bqDao(userToken.value).getQueryResult(queryJob).futureValue
        assertExpectedShakespeareResult(queryResult)

        Orchestration.billing.removeGoogleRoleFromBillingProjectUser(projectName, user.email, role)(ownerToken)

        val postRoleFailure = Retry.retry(1.second, 10.seconds) {
          // retry this because removing google roles is not always immediate
          Try { bqDao(userToken.value).startQuery(GoogleProject(projectName), shakespeareQuery).failed.futureValue }.toOption
        }.get

        postRoleFailure shouldBe a[GoogleJsonResponseException]
        preRoleFailure.getMessage should include(user.email)
        preRoleFailure.getMessage should include(projectName)
        preRoleFailure.getMessage should include("bigquery.jobs.create")
      }(ownerToken)
    }

    "should not allow access alteration for arbitrary google roles" in {
      val ownerUser: Credentials = UserPool.chooseProjectOwner
      val ownerToken: AuthToken = ownerUser.makeAuthToken()
      val roles = Seq("bigquery.admin", "bigquery.dataEditor", "bigquery.user")

      val user: Credentials = UserPool.chooseStudent

      withBillingProject("auto-goog-role") { projectName =>
        roles foreach { role =>
          val addEx = intercept[RestException] {
            Orchestration.billing.addGoogleRoleToBillingProjectUser(projectName, user.email, role)(ownerToken)
          }
          addEx.getMessage should include(role)

          val removeEx = intercept[RestException] {
            Orchestration.billing.removeGoogleRoleFromBillingProjectUser(projectName, user.email, role)(ownerToken)
          }
          removeEx.getMessage should include(role)
        }
      }(ownerToken)
    }

    "should not allow access alteration by non-owners" in {
      val Seq(userA: Credentials, userB: Credentials) = UserPool.chooseStudents(2)
      val userAToken: AuthToken = userA.makeAuthToken()

      val role = "bigquery.jobUser"
      val errorMsg = "You must be a project owner"
      val unownedProject = "broad-dsde-dev"

      val addEx = intercept[RestException] {
        Orchestration.billing.addGoogleRoleToBillingProjectUser(unownedProject, userB.email, role)(userAToken)
      }
      addEx.getMessage should include(errorMsg)
      addEx.getMessage should include(StatusCodes.Forbidden.intValue.toString)

      val removeEx = intercept[RestException] {
        Orchestration.billing.removeGoogleRoleFromBillingProjectUser(unownedProject, userB.email, role)(userAToken)
      }
      removeEx.getMessage should include(errorMsg)
      removeEx.getMessage should include(StatusCodes.Forbidden.intValue.toString)
    }
  }
}