package org.broadinstitute.dsde.firecloud.test.terraredirect

import akka.http.scaladsl.model.Uri
import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.firecloud.page.methodrepo.MethodRepoPage
import org.broadinstitute.dsde.workbench.config.UserPool
import org.broadinstitute.dsde.workbench.fixture.{BillingFixtures, TestReporterFixture, WorkspaceFixtures}
import org.broadinstitute.dsde.workbench.service.test.{CleanUp, WebBrowserSpec}
import org.scalatest.{BeforeAndAfterAll, FreeSpec, Matchers}


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

  // TODO: this test sends traffic from a FiaB or other test environment directly to the Terra dev server.
  //  tests will fail if the dev server is unresponsive, and tests will cause additional load on the dev server.
  //  is this acceptable?
  private def isTerraUrl(url: String) = {
    // should this use an environment-specific full hostname instead of contains()?
    url.contains("terra.bio") ||
    (url.contains("saturn") && url.contains("appspot.com"))
  }

  // negative test - ensure that Library/Methods Repo/whatever does NOT redirect
  "A user who visits FireCloud should NOT be redirected to the same location in Terra for" - {
    "methods repo" in {
      implicit val authToken = user.makeAuthToken()
      withWebDriver { implicit driver =>
        withSignIn(user) { _ =>
          val methodRepoPage = new MethodRepoPage().open
          // give the page a chance to issue a redirect, if a redirect mistakenly existed on this page
          Thread.sleep(1000)
          // are we still on the methods repo page?
          await ready methodRepoPage.methodRepoTable
        }
      }
    }
  }


  "A user who visits FireCloud should be redirected to the same location in Terra for" - {
    implicit val authToken = user.makeAuthToken()
    withCleanBillingProject(user) { billingProject =>
      withWorkspace(billingProject, "Terra_redirect_spec") { workspaceName =>

        "workspace summary page" in {
          withWebDriver { implicit driver =>
            // TODO: withSignIn will throw an error at the end of its closure, when it tries to sign out the user.
            //  this happens because, due to the redirect, the user is no longer in FC and therefore the signout
            //  feature isn't available. Is this acceptable?
            withSignIn(user) { listPage =>
              val startPage = driver.getCurrentUrl
              // NB: "listPage.enterWorkspace" would fail here, because enterWorkspace's awaitReady never
              //   fires due to the redirect. So we use clickWorkspaceLink instead.
              listPage.clickWorkspaceLink(billingProject, workspaceName)
              await condition (driver.getCurrentUrl != startPage)
              await condition isTerraUrl(driver.getCurrentUrl)
              val uri = Uri(driver.getCurrentUrl)
              uri.fragment shouldBe Some(s"workspaces/$billingProject/$workspaceName")
              uri.rawQueryString shouldBe Some("fcredir=1")
            }
          }
        }

      }
    }
  }

}
