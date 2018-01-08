package org.broadinstitute.dsde.firecloud.test.api.orch

import akka.http.scaladsl.model.StatusCodes
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.services.bigquery.model.GetQueryResultsResponse
import org.broadinstitute.dsde.firecloud.api.{APIException, Orchestration, Rawls}
import org.broadinstitute.dsde.firecloud.auth.AuthToken
import org.broadinstitute.dsde.firecloud.config.{Credentials, UserPool}
import org.broadinstitute.dsde.firecloud.fixture.BillingFixtures
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{FreeSpec, Matchers}
import org.broadinstitute.dsde.firecloud.dao.Google.googleBigQueryDAO
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

class OrchestrationApiSpec extends FreeSpec with Matchers with ScalaFutures with Eventually
  with BillingFixtures {
  implicit override val patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(5, Seconds)))

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
        val preRoleFailure = googleBigQueryDAO.startQuery(userToken.value, GoogleProject(projectName), "meh").failed.futureValue
        preRoleFailure shouldBe a[GoogleJsonResponseException]
        preRoleFailure.getMessage should include(user.email)
        preRoleFailure.getMessage should include(projectName)
        preRoleFailure.getMessage should include("bigquery.jobs.create")

        Orchestration.billing.addGoogleRoleToBillingProjectUser(projectName, user.email, role)(ownerToken)

        val queryReference = googleBigQueryDAO.startQuery(userToken.value, GoogleProject(projectName), shakespeareQuery).futureValue
        val queryJob = eventually {
          val job = googleBigQueryDAO.getQueryStatus(userToken.value, queryReference).futureValue
          job.getStatus.getState shouldBe "DONE"
          job
        }

        val queryResult = googleBigQueryDAO.getQueryResult(userToken.value, queryJob).futureValue
        assertExpectedShakespeareResult(queryResult)

        Orchestration.billing.removeGoogleRoleFromBillingProjectUser(projectName, user.email, role)(ownerToken)

        val postRoleFailure = googleBigQueryDAO.startQuery(userToken.value, GoogleProject(projectName), shakespeareQuery).failed.futureValue
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
          val addEx = intercept[APIException] {
            Orchestration.billing.addGoogleRoleToBillingProjectUser(projectName, user.email, role)(ownerToken)
          }
          addEx.getMessage should include(role)

          val removeEx = intercept[APIException] {
            Orchestration.billing.removeGoogleRoleFromBillingProjectUser(projectName, user.email, role)(ownerToken)
          }
          removeEx.getMessage should include(role)
        }
      }(ownerToken)
    }

    "should not allow access alteration by non-owners" in {
      val ownerUser: Credentials = UserPool.chooseProjectOwner
      val ownerToken: AuthToken = ownerUser.makeAuthToken()
      val role = "bigquery.jobUser"

      val user: Credentials = UserPool.chooseStudent

      val errorMsg = "You must be a project owner"
      val unownedProject = "broad-dsde-production"

      val addEx = intercept[APIException] {
        Orchestration.billing.addGoogleRoleToBillingProjectUser(unownedProject, user.email, role)(ownerToken)
      }
      addEx.getMessage should include(errorMsg)
      addEx.getMessage should include(StatusCodes.Forbidden.intValue.toString)

      val removeEx = intercept[APIException] {
        Orchestration.billing.removeGoogleRoleFromBillingProjectUser(unownedProject, user.email, role)(ownerToken)
      }
      removeEx.getMessage should include(errorMsg)
      removeEx.getMessage should include(StatusCodes.Forbidden.intValue.toString)
    }
  }
}