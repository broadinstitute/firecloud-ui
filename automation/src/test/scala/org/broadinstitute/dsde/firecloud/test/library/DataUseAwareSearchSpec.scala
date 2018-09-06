package org.broadinstitute.dsde.firecloud.test.library

import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.firecloud.page.library.DataLibraryPage
import org.broadinstitute.dsde.firecloud.test.ModalUtil
import org.broadinstitute.dsde.workbench.config.UserPool
import org.broadinstitute.dsde.workbench.fixture.{TestReporterFixture, WorkspaceFixtures}
import org.broadinstitute.dsde.workbench.service.test.WebBrowserSpec
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{FreeSpec, Matchers}


class DataUseAwareSearchSpec extends FreeSpec with WebBrowserSpec with UserFixtures with WorkspaceFixtures
  with Matchers with ModalUtil with Eventually with TestReporterFixture {

  override implicit val patienceConfig = PatienceConfig(timeout = scaled(Span(60, Seconds)), interval = scaled(Span(1, Seconds)))

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

        eventually { modal.isVisible shouldBe false }

        eventually { page.showsResearchPurposeCode("control") shouldBe true }
        eventually { page.showsResearchPurposeCode("poa") shouldBe true }
        eventually { page.showsResearchPurposeCode("commercial") shouldBe false } // We didn't select this one
      }
    }

    "The ontology autocomplete exists and works" in withWebDriver { implicit driver =>
      val user = UserPool.chooseAnyUser

      withSignIn(user) { _ =>
        val page = new DataLibraryPage().open
        val researchPurposeModal = page.openResearchPurposeModal()

        researchPurposeModal.selectCheckbox("disease-focused-research")

        // Disease 1
        val ffiSuggestionText = "fatal familial insomnia"
        val ffiSuggestionId = "suggestion-http://purl.obolibrary.org/obo/DOID_0050433"  // fatal familial insomnia
        val ffiTagId = "doid:0050433-tag"

        val foundSuggestions = researchPurposeModal.enterOntologySearchText("fatal")

        logger.warn(s"==========>>>>>>>>>> researchPurposeModal.enterOntologySearchText foundSuggestions: ${foundSuggestions.toList}")

        foundSuggestions.exists(_.contains(ffiSuggestionText)) shouldBe true

        researchPurposeModal.selectSuggestion(ffiSuggestionId)
        eventually { researchPurposeModal.isTagSelected(ffiTagId) shouldBe true }

        // Disease 2
        val brxSuggestionText = "bruxism"
        val brxSuggestionId = "suggestion-http://purl.obolibrary.org/obo/DOID_2846"
        val brxTagId = "doid:2846-tag"

        researchPurposeModal.enterOntologySearchText("brux").exists(_.contains(brxSuggestionText)) shouldBe true

        researchPurposeModal.selectSuggestion(brxSuggestionId)

        eventually { researchPurposeModal.isTagSelected(brxTagId) shouldBe true }
        eventually { researchPurposeModal.isTagSelected(ffiTagId) shouldBe true } // previously-selected tag still there

        researchPurposeModal.xOut()
      }
    }
  }

}
