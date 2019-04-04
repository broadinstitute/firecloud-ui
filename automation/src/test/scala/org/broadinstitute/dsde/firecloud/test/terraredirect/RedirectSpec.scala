package org.broadinstitute.dsde.firecloud.test.terraredirect

import akka.http.scaladsl.model.Uri
import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.component.{Label, TestId}
import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.workbench.config.{Credentials, UserPool}
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceListPage
import org.broadinstitute.dsde.workbench.fixture.{BillingFixtures, TestReporterFixture, WorkspaceFixtures}
import org.broadinstitute.dsde.workbench.service.test.{CleanUp, WebBrowserSpec}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FreeSpec, Matchers}

import scala.util.{Failure, Success, Try}


/**
  * This spec asserts that a user who loads specific pages in the FireCloud UI is appropriately
  * redirected to the Terra UI.
  *
  * As of this writing, redirects are disabled, because the redirects will break OTHER tests.
  * To verify THIS test passes, you must do two things:
  *   1) unignore any test you want to run, inside this file
  *   2) set the UI's flag to enable redirects. You can do this by either or:
  *     a) changing "terraRedirectsEnabled" to true in the UI instance's config.json
  *     b) in the UI's config.cljs, change the terra-redirects-enabled function to return a hardcoded
  *         value of true instead of returning (get @config "terraRedirectsEnabled" false)
  */
class RedirectSpec extends FreeSpec with BeforeAndAfterAll with Matchers with WebBrowserSpec
  with UserFixtures with WorkspaceFixtures with BillingFixtures with CleanUp with LazyLogging with TestReporterFixture {

  val user = UserPool.chooseAnyUser
  var billingProject = ""
  var workspaceName = ""

  private def isTerraUrl(url: String) = {
    // should this use an environment-specific full hostname instead of contains()?
    url.contains("terra.bio") ||
    (url.contains("saturn") && url.contains("appspot.com"))
  }

//  // beforeAll, create new workspace
//  override protected def beforeAll(): Unit = {
//    // TODO: must fix!!! Can't use withWorkspace/withCleanBillingProject here because they release
//    // at the end of their closures
//    implicit val authToken = user.makeAuthToken()
//    withCleanBillingProject(user) { bp =>
//      billingProject = bp
//      withWorkspace(billingProject, "Terra_redirect_spec") { ws =>
//        workspaceName = ws
//        api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)
//        logger.info(s"seed workspace created: $workspaceName")
//        super.beforeAll()
//      }
//    }
//  }
//
//  override protected def afterAll(): Unit = {
//    Try(api.workspaces.delete(billingProject, workspaceName)(user.makeAuthToken())) match {
//      case Success(_) => //noop
//      case Failure(ex) =>
//        logger.warn(s"failed to clean up workspace $billingProject/$workspaceName")
//    }
//    super.afterAll()
//  }

  // TODO: negative test - ensure that Library/Methods Repo/whatever does NOT redirect

  "A user who visits FireCloud should be redirected to the same location in Terra for" - {
    "workspace summary page" in {
      implicit val authToken = user.makeAuthToken()
      withCleanBillingProject(user) { bp =>
        billingProject = bp
        withWorkspace(billingProject, "Terra_redirect_spec") { ws =>
          workspaceName = ws

          withWebDriver { implicit driver =>
            withSignIn(user) { listPage =>
              val detailPage = listPage.enterWorkspace(billingProject, workspaceName)
              await condition (isTerraUrl(driver.getCurrentUrl))
              val uri = Uri(driver.getCurrentUrl)
              logger.info(s"Terra redirect test found url: [${uri.toString}]")
              uri.fragment shouldBe Some(s"workspaces/$billingProject/$workspaceName")
              uri.rawQueryString shouldBe Some("fcredir=1")
            }
          }
        }
      }
    }
  }


}
