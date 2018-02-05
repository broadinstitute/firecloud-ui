package org.broadinstitute.dsde.firecloud.test.library

import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.firecloud.page.library.DataLibraryPage
import org.broadinstitute.dsde.firecloud.test.ModalUtil
import org.broadinstitute.dsde.workbench.config.UserPool
import org.broadinstitute.dsde.workbench.fixture.WorkspaceFixtures
import org.broadinstitute.dsde.workbench.service.test.{CleanUp, WebBrowserSpec}
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class DataUseAwareSearchSpec extends FreeSpec with WebBrowserSpec with UserFixtures with WorkspaceFixtures with CleanUp with Matchers with ModalUtil {
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  // We are only testing UI mechanics because the business logic of RP matching is extensively tested lower in the stack.
  "Data Library" - {
    "Select options in the RP modal and generate breadcrumbs" in withWebDriver { implicit driver =>
      val user = UserPool.chooseAnyUser

      withSignIn(user) { _ =>
        val page = new DataLibraryPage().open

        testModal(page.openResearchPurposeModal())

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
        val researchPurposeModal = page.openResearchPurposeModal()

        researchPurposeModal.selectCheckbox("disease-focused-research")

        // Disease 1
        val ffiSuggestionId = "suggestion-http://purl.obolibrary.org/obo/DOID_0050433"  // fatal familial insomnia
        val ffiTagId = "doid:0050433-tag"

        researchPurposeModal.enterOntologySearchText("fatal")

        researchPurposeModal.isSuggestionVisible(ffiSuggestionId) shouldBe true

        researchPurposeModal.selectSuggestion(ffiSuggestionId, ffiTagId)

        researchPurposeModal.isTagSelected(ffiTagId) shouldBe true

        // Disease 2
        val brxSuggestionId = "suggestion-http://purl.obolibrary.org/obo/DOID_2846" // bruxism
        val brxTagId = "doid:2846-tag"

        researchPurposeModal.enterOntologySearchText("brux")

        researchPurposeModal.isSuggestionVisible(brxSuggestionId) shouldBe true

        researchPurposeModal.selectSuggestion(brxSuggestionId, brxTagId)

        researchPurposeModal.isTagSelected(brxTagId) shouldBe true
        researchPurposeModal.isTagSelected(ffiTagId) shouldBe true // previously-selected tag still there
      }
    }
  }

}
