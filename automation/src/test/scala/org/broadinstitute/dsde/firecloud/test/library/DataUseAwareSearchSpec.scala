package org.broadinstitute.dsde.firecloud.test.library

import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.firecloud.component.{Button, Label, SearchField}
import org.broadinstitute.dsde.firecloud.config.UserPool
import org.broadinstitute.dsde.firecloud.fixture.{UserFixtures, WorkspaceFixtures}
import org.broadinstitute.dsde.firecloud.page.library.DataLibraryPage
import org.broadinstitute.dsde.firecloud.test.{CleanUp, WebBrowserSpec}
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class DataUseAwareSearchSpec extends FreeSpec with WebBrowserSpec with UserFixtures with WorkspaceFixtures with CleanUp with Matchers {
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  // We are only testing UI mechanics because the business logic of RP matching is extensively tested lower in the stack.
  "Data Library" - {
    "Repeatedly open and close the RP modal" in withWebDriver { implicit driver =>
      val user = UserPool.chooseAnyUser

      withSignIn(user) { _ =>
        val page = new DataLibraryPage().open

        page.isShowingResearchPurposeModal shouldBe false

        page.openResearchPurposeModal()
        page.isShowingResearchPurposeModal shouldBe true

        page.dismissResearchPurposeModal()
        page.isShowingResearchPurposeModal shouldBe false

        page.openResearchPurposeModal()
        page.isShowingResearchPurposeModal shouldBe true

        page.dismissResearchPurposeModal()
        page.isShowingResearchPurposeModal shouldBe false
      }
    }

    "Select options in the RP modal and generate breadcrumbs" in withWebDriver { implicit driver =>
      val user = UserPool.chooseAnyUser

      withSignIn(user) { _ =>
        val page = new DataLibraryPage().open

        page.openResearchPurposeModal()

        page.selectRPCheckbox("control")
        page.selectRPCheckbox("poa")

        page.executeRPSearch()

        page.isShowingResearchPurposeModal shouldBe false

        Label("control-tag").isVisible shouldBe true
        Label("poa-tag").isVisible shouldBe true
        Label("commercial-tag").isVisible shouldBe false // We didn't select this one
      }
    }

    "The ontology autocomplete exists and works" in withWebDriver { implicit driver =>
      val user = UserPool.chooseAnyUser

      withSignIn(user) { _ =>
        val page = new DataLibraryPage().open

        page.openResearchPurposeModal()

        page.selectRPCheckbox("disease-focused-research")

        SearchField("ontology-autosuggest").setText("fatal")

        val suggestionId = "suggestion-http://purl.obolibrary.org/obo/DOID_0050433"  // fatal familial insomnia
        val tagId = "doid:0050433-tag"

        await enabled testId(suggestionId) // has a timeout so test will not hang if suggestion never shows

        val ffi = Button(suggestionId)
        ffi.isVisible shouldBe true

        ffi.doClick()

        await enabled testId(tagId)

        Label(tagId).isVisible shouldBe true
      }
    }
  }

}
