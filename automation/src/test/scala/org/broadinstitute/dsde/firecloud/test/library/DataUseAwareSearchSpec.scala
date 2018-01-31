package org.broadinstitute.dsde.firecloud.test.library

import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.firecloud.component.{Button, Label, SearchField}
import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.firecloud.page.library.DataLibraryPage
import org.broadinstitute.dsde.workbench.config.UserPool
import org.broadinstitute.dsde.workbench.fixture.WorkspaceFixtures
import org.broadinstitute.dsde.workbench.service.test.{CleanUp, WebBrowserSpec}
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

        val modal = page.openResearchPurposeModal()
        modal.isVisible shouldBe true

        modal.cancel()
        modal.isVisible shouldBe false

        page.openResearchPurposeModal()
        modal.isVisible shouldBe true

        modal.cancel()
        modal.isVisible shouldBe false
      }
    }

    "Select options in the RP modal and generate breadcrumbs" in withWebDriver { implicit driver =>
      val user = UserPool.chooseAnyUser

      withSignIn(user) { _ =>
        val page = new DataLibraryPage().open

        val modal = page.openResearchPurposeModal()

        modal.selectCheckbox("control")
        modal.selectCheckbox("poa")
        modal.submit()

        modal.isVisible shouldBe false

        page.showsResearchPurposeCode("control") shouldBe true
        page.showsResearchPurposeCode("poa") shouldBe true
        page.showsResearchPurposeCode("commercial") shouldBe false // We didn't select this one
      }
    }

    "The ontology autocomplete exists and works" in withWebDriver { implicit driver =>
      val user = UserPool.chooseAnyUser

      withSignIn(user) { _ =>
        val page = new DataLibraryPage().open

        page.openResearchPurposeModal()

        page.selectRPCheckbox("disease-focused-research")

        // Disease 1
        val ffiSuggestionId = "suggestion-http://purl.obolibrary.org/obo/DOID_0050433"  // fatal familial insomnia
        val ffiTagId = "doid:0050433-tag"

        page.enterRPOntologySearchText("fatal")

        page.isSuggestionVisible(ffiSuggestionId) shouldBe true

        page.selectSuggestion(ffiSuggestionId, ffiTagId)

        page.isTagSelected(ffiTagId) shouldBe true

        // Disease 2
        val brxSuggestionId = "suggestion-http://purl.obolibrary.org/obo/DOID_2846" // bruxism
        val brxTagId = "doid:2846-tag"

        page.enterRPOntologySearchText("brux")

        page.isSuggestionVisible(brxSuggestionId) shouldBe true

        page.selectSuggestion(brxSuggestionId, brxTagId)

        page.isTagSelected(brxTagId) shouldBe true
        page.isTagSelected(ffiTagId) shouldBe true // previously-selected tag still there
      }
    }
  }

}
