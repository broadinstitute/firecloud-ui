package org.broadinstitute.dsde.firecloud.test.library

import org.broadinstitute.dsde.firecloud.fixture.{LibraryData, UserFixtures}
import org.broadinstitute.dsde.firecloud.page.library.DataLibraryPage
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{Config, UserPool}
import org.broadinstitute.dsde.workbench.fixture.WorkspaceFixtures
import org.broadinstitute.dsde.workbench.service.test.WebBrowserSpec
import org.scalatest.{BeforeAndAfterAll, FreeSpec, Matchers}

class TagSearchSpec extends FreeSpec with WebBrowserSpec with WorkspaceFixtures with UserFixtures with Matchers with BeforeAndAfterAll {

  val namespace: String = Config.Projects.default
  val curatorUser = UserPool.chooseCurator
  implicit val curatorAuthToken: AuthToken = curatorUser.makeAuthToken()
  var data: Map[String, Any] = _
  var newWsName: String = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    withWorkspace(namespace, "PublishSpec_curator_publish_") { wsName =>
      withCleanUp {
        data = LibraryData.metadata + ("library:datasetName" -> wsName)
        api.library.setLibraryAttributes(namespace, wsName, data)
        register cleanUp api.library.unpublishWorkspace(namespace, wsName)
        api.library.publishWorkspace(namespace, wsName)
        logger.info("Finished create and published Workspace: %s/%s".format(namespace, wsName))
      }
      newWsName = wsName
    }
  }

  override def afterAll(): Unit = {
    super.afterAll()
  }


  "a published workspace" - {

    "Tags search with No Results Found in Data Library table" in {
      withWebDriver { implicit driver =>
        withSignIn(curatorUser) { _ =>
          logger.info("Log in user as " + curatorUser.email)
          val page = new DataLibraryPage().open

          val found = page.doTagsSearch("tag1")
          found shouldBe false
        }
      }
    }


    "should see pass" in {
      withWebDriver { implicit driver =>

        logger.info("pass")
      }
    }

  }

}
